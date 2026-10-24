package dev.kultivator.client.dunia.tempat.hidup;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.voice.VoiceSettings;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.sound.SuaraKota;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * The crowd of an inhabited place, heard ("Kota yang Hidup", docs/progres/suara-kota.md): one looping bed per kind of
 * place ({@link SuaraKota}) whose volume follows how many townsfolk are around the player. Client only and without any
 * payload: the kind comes from the synced roles of the near bodies ({@link KultivatorNpc#role()}) plus the far crowd the
 * server already sends ({@link ImpostorRenderer#dengar}), so the capital, themed cities, towns, villages and NPC sect
 * compounds all sound right without the client knowing the plan. Vendors make a market, innkeepers and cooks a tavern or
 * tea house, sailors and fishers (or open water around) a harbour, monks and sect elders a temple, everyone else a street.
 * <p>
 * Night (the capital's curfew, villages asleep), rain and a roof over the head turn it down, the tavern least of all; a
 * scan runs once a second (every two at Minimal quality) and each bed slews its volume, so places fade in and out. Volume:
 * "Riuh kota" ({@link VoiceSettings#riuh()}) times the vanilla "Ambient/Environment" slider.
 */
public final class RiuhKota {

	/** The kinds of place, in {@link SuaraKota#RIUH} order. */
	enum Suasana {
		PASAR, KEDAI, PELABUHAN, JALAN, KUIL
	}

	/** Loudest bed (the loops are mastered quiet already; this leaves room for the town's own sounds). */
	private static final float MAKS = 0.75F;
	/** Near bodies count fully inside this radius and fade to nothing at {@link #DEKAT}. */
	private static final double DEKAT = 32.0D;
	private static final double JAUH = 112.0D;

	private static final @Nullable Lingkup[] BED = new Lingkup[Suasana.values().length];
	private static final float[] SKOR = new float[Suasana.values().length];
	private static final float[] TARGET = new float[Suasana.values().length];
	private static int detak;

	private RiuhKota() {
	}

	/** End of every client tick. */
	static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;
		if (player == null || level == null) {
			bersihkan();
			return;
		}
		if (minecraft.isPaused()) {
			return;
		}
		if (++detak >= (EffectQuality.minimal() ? 40 : 20)) {
			detak = 0;
			dengar(level, player);
		}
		Suasana[] semua = Suasana.values();
		for (int i = 0; i < semua.length; i++) {
			Lingkup bed = BED[i];
			if (bed != null && !bed.isStopped()) {
				bed.target = TARGET[i];
			} else if (TARGET[i] > 0.01F) {
				Lingkup fresh = new Lingkup(SuaraKota.RIUH.get(i));
				fresh.target = TARGET[i];
				try {
					minecraft.getSoundManager().play(fresh);
					BED[i] = fresh;
				} catch (RuntimeException exception) {
					BED[i] = null;
				}
			} else {
				BED[i] = null;
			}
		}
	}

	/** Disconnect or no world: every bed fades out (the sound engine drops them with the level anyway). */
	static void bersihkan() {
		for (int i = 0; i < TARGET.length; i++) {
			TARGET[i] = 0.0F;
			if (BED[i] != null) {
				BED[i].target = 0.0F;
			}
		}
	}

	/** Listens around the player: the kind scores and the crowd size, turned into each bed's target volume. */
	private static void dengar(ClientLevel level, LocalPlayer player) {
		Arrays.fill(SKOR, 0.0F);
		float setelan = VoiceSettings.riuh();
		if (setelan <= 0.0F) {
			Arrays.fill(TARGET, 0.0F);
			return;
		}
		double px = player.getX();
		double pz = player.getZ();
		float orang = 0.0F;
		for (KultivatorNpc npc : level.getEntitiesOfClass(KultivatorNpc.class, player.getBoundingBox().inflate(DEKAT, 16.0D, DEKAT),
				n -> n.isAlive() && !n.hasFlag(KultivatorNpc.FLAG_HOSTILE) && !n.hasFlag(KultivatorNpc.FLAG_GHOST))) {
			float w = (float) Mth.clamp(1.0D - npc.distanceTo(player) / DEKAT, 0.0D, 1.0D) * 0.8F + 0.2F;
			orang += w;
			nilai(npc.role(), w);
		}
		// the far crowd (48-128 blocks, sent by the server for the impostors): a soft hum of the rest of the town
		float[] jauh = {0.0F};
		int kursi = ImpostorRenderer.dengar(px, pz, JAUH, (role, d) -> {
			float w = (float) (0.15D * (1.0D - d / JAUH));
			jauh[0] += w;
			nilai(role, w);
		});
		orang += jauh[0] + kursi * 0.08F;
		SKOR[Suasana.JALAN.ordinal()] += kursi * 0.08F;
		if (orang < 1.0F) {
			Arrays.fill(TARGET, 0.0F);
			return;
		}
		// open water around an inhabited spot: a harbour, a river town's quay
		SKOR[Suasana.PELABUHAN.ordinal()] += air(level, player.blockPosition()) * 0.12F * Math.min(orang, 6.0F);
		long jam = Math.floorMod(level.getOverworldClockTime(), 24000L);
		boolean malam = jam >= 13000L && jam < 23000L;
		boolean atap = !level.canSeeSky(BlockPos.containing(player.getEyePosition()));
		boolean hujan = level.isRaining();
		float ramai = Mth.clamp((orang - 1.0F) / 10.0F, 0.0F, 1.0F);
		float puncak = 0.0F;
		for (float s : SKOR) {
			puncak = Math.max(puncak, s);
		}
		for (Suasana s : Suasana.values()) {
			int i = s.ordinal();
			float bagian = puncak <= 0.0F ? 0.0F : SKOR[i] / puncak;
			// only the leading kind and close runners-up play, so a market street can carry some tavern laughter
			float v = bagian < 0.55F ? 0.0F : bagian * bagian;
			boolean kedai = s == Suasana.KEDAI;
			if (malam) {
				v *= kedai ? 0.9F : s == Suasana.KUIL ? 0.6F : 0.35F;
			}
			if (atap) {
				v *= kedai ? 1.0F : 0.5F;
			}
			if (hujan) {
				v *= kedai ? 0.9F : 0.6F;
			}
			TARGET[i] = Math.min(MAKS, v * ramai * setelan * MAKS);
		}
	}

	/** Adds one townsperson of {@code role} to the kind scores. */
	private static void nilai(NpcRole role, float w) {
		SKOR[Suasana.JALAN.ordinal()] += w * 0.6F;
		switch (role) {
			case PEMILIK_TEH, PEMILIK_PENGINAPAN, KOKI, PELAYAN, AHLI_TEH -> SKOR[Suasana.KEDAI.ordinal()] += w * 2.2F;
			case NELAYAN, NAKHODA, KULI, PENDAYUNG -> SKOR[Suasana.PELABUHAN.ordinal()] += w * 1.6F;
			case TETUA_SEKTE, MURID_SEKTE_LUAR, MURID_SEKTE_DALAM, PELAYAN_SEKTE, PENCERAMAH, PENCERAMAH_FANA, PUSTAKAWAN ->
					SKOR[Suasana.KUIL.ordinal()] += w * 1.2F;
			default -> {
				if (NpcRole.Tema.PELABUHAN.equals(role.tema)) {
					SKOR[Suasana.PELABUHAN.ordinal()] += w * 1.6F;
				} else if (role.kelompok == NpcRole.Kelompok.RAHIB) {
					SKOR[Suasana.KUIL.ordinal()] += w * 1.8F;
				} else if (role.kelompok == NpcRole.Kelompok.PEDAGANG) {
					SKOR[Suasana.PASAR.ordinal()] += w * 1.5F;
				}
			}
		}
	}

	/** Water samples on a 7x7 grid (4 blocks apart) around and just below the player: 0..49. */
	private static int air(ClientLevel level, BlockPos pusat) {
		int n = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -12; dx <= 12; dx += 4) {
			for (int dz = -12; dz <= 12; dz += 4) {
				for (int dy = -1; dy >= -3; dy--) {
					pos.set(pusat.getX() + dx, pusat.getY() + dy, pusat.getZ() + dz);
					if (level.getFluidState(pos).is(FluidTags.WATER)) {
						n++;
						break;
					}
				}
			}
		}
		return n;
	}

	/** One looping bed heard all around the listener; it slews to its target and stops itself after a quiet second. */
	private static final class Lingkup extends AbstractTickableSoundInstance {

		float target;
		private int sepi;

		Lingkup(SoundEvent event) {
			super(event, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
			this.looping = true;
			this.delay = 0;
			this.volume = 0.0001F;
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
		}

		@Override
		public void tick() {
			// about three seconds from silence to full: walking through a gate fades the town in, not on
			float step = 0.012F;
			volume = volume < target ? Math.min(target, volume + step) : Math.max(target, volume - step);
			if (target <= 0.0F && volume < 0.002F) {
				volume = 0.0001F;
				if (++sepi > 20) {
					stop();
				}
			} else {
				sepi = 0;
			}
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}
	}
}
