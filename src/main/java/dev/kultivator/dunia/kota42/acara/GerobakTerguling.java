package dev.kultivator.dunia.kota42.acara;

import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.api.KotaGerakId;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiEntities;
import dev.kultivator.dunia.tempat.ekonomi.GerobakEntity;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * An overturned cart (DESIGN 42.7.4 "gerobak terguling", daytime on a street node): a {@code GerobakEntity} lies on its side
 * (synced TERGULING: tilted 70 degrees, a wheel spinning) with 6-10 {@code barang_tercecer} goods strewn around it, and its
 * owner, a resident of the town, wrings his hands beside it (clip 393 {@code meratap}, voice {@code kota42.gerobak}).
 * <p>
 * Players help with the mouse: a right-click on the cart lifts it (a 3-second channel within 3.5 blocks; Foundation and
 * above heave it up at once, clip 392 {@code angkat_gerobak} on the owner), a right-click on each spilled thing gathers it.
 * When the cart stands and the goods are back, every helper gets the owner's thanks (the resident's {@code bantu} memory
 * and gossip {@code MENOLONG}) and a little of the load (bread, apples or a honey jar). Left alone for 3 minutes, two
 * neighbours come and help instead.
 */
final class GerobakTerguling extends PeristiwaJalan.Kejadian {

	static final int TUNGGU_BANTU = 3600;
	static final int KANAL = 60;
	static final int BATAS = TUNGGU_BANTU + 2400;
	static final double JARAK_ANGKAT = 3.5D;

	private final GerobakEntity gerobak;
	private final List<BarangTercecerEntity> barang = new ArrayList<>();
	private final Map<UUID, Long> angkat = new HashMap<>();
	private final Set<UUID> penolong = new HashSet<>();
	private final List<Warga> tetangga = new ArrayList<>();
	private final BlockPos pos;
	private @Nullable Warga pemilik;
	private boolean tegak;
	private boolean disapa;
	private long bantuT = -1L;
	private int total;

	private GerobakTerguling(TempatHuni huni, long now, GerobakEntity gerobak, BlockPos pos) {
		super(PeristiwaJalan.GEROBAK, huni, now);
		this.gerobak = gerobak;
		this.pos = pos;
	}

	// ================================================================== start

	static @Nullable GerobakTerguling mulai(ServerLevel level, TempatHuni huni, ServerPlayer player, long now) {
		RandomSource r = level.getRandom();
		List<BlockPos> calon = new ArrayList<>();
		for (Titik t : huni.titik("jalan")) {
			double d = t.pos().distSqr(player.blockPosition());
			if (d >= 8.0D * 8.0D && d <= 40.0D * 40.0D) {
				calon.add(t.pos());
			}
		}
		if (calon.isEmpty()) {
			return null;
		}
		BlockPos spot = PeristiwaJalan.berdiri(level, calon.get(r.nextInt(calon.size())));
		if (spot == null || !DuniaBudget.mayAdd(level, KotaAcaraEntities.ANGGARAN)) {
			return null;
		}
		GerobakEntity cart = EkonomiEntities.GEROBAK.create(level, EntitySpawnReason.EVENT);
		if (cart == null) {
			return null;
		}
		float yaw = r.nextFloat() * 360.0F;
		cart.snapTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, yaw, 0.0F);
		cart.setMuatan(r.nextInt(3));
		cart.setTerguling(true);
		cart.pegang(now + 200L);
		if (!level.addFreshEntity(cart)) {
			return null;
		}
		GerobakTerguling k = new GerobakTerguling(huni, now, cart, spot);
		int n = 6 + r.nextInt(5);
		int[] jenis = switch (cart.muatan()) {
			case 1 -> new int[]{BarangTercecerEntity.VARIAN_KARUNG, BarangTercecerEntity.VARIAN_SAYUR, BarangTercecerEntity.VARIAN_BUAH};
			case 2 -> new int[]{BarangTercecerEntity.VARIAN_GUCI, BarangTercecerEntity.VARIAN_GUCI, BarangTercecerEntity.VARIAN_SAYUR};
			default -> new int[]{BarangTercecerEntity.VARIAN_KAIN, BarangTercecerEntity.VARIAN_BUAH, BarangTercecerEntity.VARIAN_GUCI};
		};
		for (int i = 0; i < n && DuniaBudget.mayAdd(level, KotaAcaraEntities.ANGGARAN); i++) {
			double angle = r.nextDouble() * Math.PI * 2.0D;
			double dist = 1.2D + r.nextDouble() * 1.8D;
			BlockPos at = PeristiwaJalan.berdiri(level, BlockPos.containing(spot.getX() + 0.5D + Math.cos(angle) * dist, spot.getY(),
					spot.getZ() + 0.5D + Math.sin(angle) * dist));
			if (at == null) {
				continue;
			}
			BarangTercecerEntity thing = KotaAcaraEntities.BARANG_TERCECER.create(level, EntitySpawnReason.EVENT);
			if (thing == null) {
				break;
			}
			thing.snapTo(at.getX() + 0.2D + r.nextDouble() * 0.6D, at.getY(), at.getZ() + 0.2D + r.nextDouble() * 0.6D, r.nextFloat() * 360.0F, 0.0F);
			thing.ikat(huni.situsId, spot.asLong());
			thing.setVarian(jenis[r.nextInt(jenis.length)]);
			thing.setNilai(r.nextFloat());
			thing.pegang(now + 200L);
			if (level.addFreshEntity(thing)) {
				k.barang.add(thing);
			}
		}
		k.total = k.barang.size();
		k.pemilik = pemilik(huni, r);
		if (k.pemilik != null) {
			PeristiwaJalan.tugas(k.pemilik, huni.situsId, PeristiwaJalan.di("gerobak_terguling", spot.offset(2, 0, 1), KotaGerakId.MERATAP, 0),
					now + BATAS + 200L);
		}
		level.playSound(null, spot, SoundEvents.WOOD_BREAK, SoundSource.NEUTRAL, 1.2F, 0.6F);
		level.sendParticles(ParticleTypes.POOF, spot.getX() + 0.5D, spot.getY() + 0.4D, spot.getZ() + 0.5D, 14, 1.0D, 0.3D, 1.0D, 0.03D);
		Component siapa = k.pemilik == null ? Component.translatable(PeristiwaJalan.L + "gerobak.seorang_pedagang") : WargaApi.nama(k.pemilik);
		PeristiwaJalan.kabar(level, Vec3.atCenterOf(spot), 40.0D, Component.translatable(PeristiwaJalan.L + "gerobak.mulai", siapa), false);
		PeristiwaJalan.kabar(level, Vec3.atCenterOf(spot), 20.0D, Component.translatable(PeristiwaJalan.L + "gerobak.petunjuk"), true);
		return k;
	}

	/** A trader, farmer or porter of the town not busy elsewhere, else any adult. */
	private static @Nullable Warga pemilik(TempatHuni huni, RandomSource r) {
		List<Warga> all = PeristiwaJalan.dewasa(huni.situsId);
		all.removeIf(PeristiwaJalan::bertugas);
		for (Warga w : all) {
			NpcRole role = w.peranNpc();
			if (role == NpcRole.PEDAGANG_KELILING || role == NpcRole.PETANI || role == NpcRole.KULI || role == NpcRole.PEDAGANG_BAZAR) {
				return w;
			}
		}
		return all.isEmpty() ? null : all.get(r.nextInt(all.size()));
	}

	// ================================================================== tick

	@Override
	boolean tik(ServerLevel level, long now) {
		if (gerobak.isRemoved()) {
			return true;
		}
		gerobak.pegang(now + 200L);
		barang.removeIf(e -> e.isRemoved() || !e.tergeletak());
		for (BarangTercecerEntity e : barang) {
			e.pegang(now + 200L);
		}
		KultivatorNpc owner = PeristiwaJalan.tubuh(huni, pemilik);
		if (owner != null && !disapa && owner.distanceToSqr(gerobak) < 6.0D * 6.0D) {
			disapa = true;
			PeristiwaJalan.seru(owner, "kota42.gerobak", "gerobak.ratap");
		}
		// lifting channels
		for (Map.Entry<UUID, Long> e : List.copyOf(angkat.entrySet())) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(e.getKey());
			if (p == null || p.distanceToSqr(gerobak) > JARAK_ANGKAT * JARAK_ANGKAT || tegak) {
				angkat.remove(e.getKey());
				if (p != null && !tegak) {
					Msg.actionBar(p, Component.translatable(PeristiwaJalan.L + "gerobak.lepas"));
				}
				continue;
			}
			long held = now - e.getValue();
			if (held >= KANAL) {
				angkat.remove(e.getKey());
				tegakkan(level, p);
			} else if (held % 10L == 0L) {
				Msg.actionBar(p, Component.translatable(PeristiwaJalan.L + "gerobak.mengangkat", (int) (held * 100L / KANAL)));
			}
		}
		// neighbours help a cart nobody helped
		if (penolong.isEmpty() && bantuT < 0L && now - mulai >= TUNGGU_BANTU) {
			bantuT = now;
			for (KultivatorNpc npc : PeristiwaJalan.tubuhDekat(level, situs, gerobak.position(), 32.0D)) {
				Warga w = WargaApi.dari(npc);
				if (w != null && w != pemilik && WargaApi.umur(w) >= 16 && !PeristiwaJalan.bertugas(w)) {
					tetangga.add(w);
					PeristiwaJalan.tugas(w, situs, PeristiwaJalan.di("bantu_gerobak", pos.offset(-1 - tetangga.size(), 0, 0),
							KotaGerakId.ANGKAT_GEROBAK, 0), now + BATAS);
					if (tetangga.size() >= 2) {
						break;
					}
				}
			}
		}
		if (bantuT >= 0L && penolong.isEmpty()) {
			long since = now - bantuT;
			if (!tegak && since >= 200L) {
				tegakkan(level, null);
			}
			if (tegak && since % 40L == 0L && !barang.isEmpty()) {
				barang.getFirst().pungut();
				barang.removeFirst();
			}
		}
		if (tegak && barang.isEmpty()) {
			selesai(level);
			return true;
		}
		return now - mulai >= BATAS;
	}

	// ================================================================== player help

	@Override
	boolean klik(ServerLevel level, ServerPlayer player, Entity e, boolean shift) {
		if (e == gerobak) {
			if (tegak) {
				return true;
			}
			if (Cultivators.get(player).realm().ordinal() >= Realm.FOUNDATION.ordinal()) {
				tegakkan(level, player);
				Msg.actionBar(player, Component.translatable(PeristiwaJalan.L + "gerobak.angkat_kuat"));
			} else if (!angkat.containsKey(player.getUUID())) {
				angkat.put(player.getUUID(), level.getGameTime());
				Msg.actionBar(player, Component.translatable(PeristiwaJalan.L + "gerobak.mulai_angkat"));
				KultivatorNpc owner = PeristiwaJalan.tubuh(huni, pemilik);
				if (owner != null) {
					HidupApi.klip(owner, KotaGerakId.ANGKAT_GEROBAK, 0, KANAL);
				}
			}
			return true;
		}
		if (e instanceof BarangTercecerEntity thing && barang.contains(thing)) {
			thing.pungut();
			barang.remove(thing);
			penolong.add(player.getUUID());
			level.playSound(null, thing.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6F, 1.1F);
			Msg.actionBar(player, Component.translatable(PeristiwaJalan.L + "gerobak.pungut", total - barang.size(), total));
			return true;
		}
		return false;
	}

	private void tegakkan(ServerLevel level, @Nullable ServerPlayer player) {
		if (tegak) {
			return;
		}
		tegak = true;
		gerobak.setTerguling(false);
		level.playSound(null, gerobak.blockPosition(), SoundEvents.WOOD_BREAK, SoundSource.NEUTRAL, 1.0F, 0.5F);
		level.sendParticles(ParticleTypes.POOF, gerobak.getX(), gerobak.getY() + 0.3D, gerobak.getZ(), 10, 0.8D, 0.2D, 0.8D, 0.02D);
		if (player != null) {
			penolong.add(player.getUUID());
		}
		KultivatorNpc owner = PeristiwaJalan.tubuh(huni, pemilik);
		if (owner != null) {
			HidupApi.klip(owner, GerakId.HORMAT, 0, 40);
			PeristiwaJalan.seru(owner, null, "gerobak.tegak");
		}
	}

	private void selesai(ServerLevel level) {
		MinecraftServer server = level.getServer();
		KultivatorNpc owner = PeristiwaJalan.tubuh(huni, pemilik);
		if (penolong.isEmpty()) {
			PeristiwaJalan.seru(owner, null, "gerobak.tetangga");
			return;
		}
		PeristiwaJalan.seru(owner, "kota42.terima_kasih", "gerobak.terima_kasih");
		if (owner != null) {
			HidupApi.klip(owner, GerakId.HORMAT_DALAM, 0, 60);
		}
		for (UUID id : penolong) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p == null || p.distanceToSqr(gerobak) > 48.0D * 48.0D) {
				continue;
			}
			if (pemilik != null) {
				WargaApi.peristiwaPemain(p, situs, "bantu", pemilik);
			}
			ItemStack upah = switch (gerobak.muatan()) {
				case 1 -> new ItemStack(Items.BREAD, 2);
				case 2 -> new ItemStack(Items.HONEY_BOTTLE, 1);
				default -> new ItemStack(Items.APPLE, 3);
			};
			PeristiwaJalan.beri(p, upah);
			Msg.chat(p, Component.translatable(PeristiwaJalan.L + "gerobak.selesai").withStyle(ChatFormatting.GOLD));
		}
	}

	@Override
	void akhiri(ServerLevel level) {
		for (BarangTercecerEntity e : barang) {
			if (!e.isRemoved()) {
				e.pungut();
			}
		}
		if (!gerobak.isRemoved()) {
			gerobak.discard();
		}
		PeristiwaJalan.lepas(pemilik);
		for (Warga w : tetangga) {
			PeristiwaJalan.lepas(w);
		}
	}
}
