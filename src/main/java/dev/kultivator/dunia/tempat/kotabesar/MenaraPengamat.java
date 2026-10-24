package dev.kultivator.dunia.tempat.kotabesar;

import dev.kultivator.dunia.api.SkyKind;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.langit.LangitItems;
import dev.kultivator.dunia.langit.SkyEvents;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Sky Observatory of the Scholars' District (DESIGN 38.B.4.4): a seven-storey pagoda whose open top deck
 * ({@code kotabesar/pengamat}) watches the heavens. During a sky event of module L a player who keeps watch on the deck
 * for thirty seconds is given, once per event night: two Spirit Meteor Stones under a Meteor Shower, a Kristal Gerhana
 * with a one-in-two chance under a Blood Eclipse (the eclipse's own daily cap is untouched), a Spirit Dew under the Twin
 * Moons. Read-only on the sky: the observatory never starts or changes an event.
 */
final class MenaraPengamat {

	static final double DEK = 7.0D;
	static final int DETIK = 30;

	private static final Map<UUID, Integer> JAGA = new HashMap<>();

	static void lupakan() {
		JAGA.clear();
	}

	/** Every 20 ticks. */
	static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		SkyKind sky = SkyEvents.active(level);
		if (sky == null || !KotaBesarConfig.hidup()) {
			if (!JAGA.isEmpty()) {
				JAGA.clear();
			}
			return;
		}
		long day = Kota.day(server);
		for (KotaBesarAnchors.KotaHidup kota : KotaBesarAnchors.semua()) {
			BlockPos deck = kota.pengamat;
			if (deck == null || kota.dimensi != level.dimension()) {
				continue;
			}
			for (ServerPlayer p : level.players()) {
				double dx = p.getX() - (deck.getX() + 0.5D);
				double dz = p.getZ() - (deck.getZ() + 0.5D);
				if (dx * dx + dz * dz > DEK * DEK || Math.abs(p.getY() - deck.getY()) > 4.0D) {
					continue;
				}
				CompoundTag mine = KotaBesarData.get(server).pemain(p.getUUID());
				String key = "pengamat_" + sky.id();
				if (mine.getLongOr(key, Long.MIN_VALUE) == day) {
					continue;
				}
				int seconds = JAGA.merge(p.getUUID(), 1, Integer::sum);
				if (seconds % 10 == 0 && seconds < DETIK) {
					Msg.actionBar(p, Component.translatable("kultivator.kota_besar.pengamat.amati", seconds, DETIK).withStyle(ChatFormatting.AQUA));
				}
				if (seconds < DETIK) {
					continue;
				}
				JAGA.remove(p.getUUID());
				mine.putLong(key, day);
				KotaBesarData.get(server).setDirty();
				switch (sky) {
					case METEOR_SHOWER -> {
						Barang.beri(p, new ItemStack(LangitItems.BATU_METEOR_ROH, 2));
						Msg.chat(p, Component.translatable("kultivator.kota_besar.pengamat.meteor").withStyle(ChatFormatting.GOLD));
					}
					case BLOOD_ECLIPSE -> {
						if (p.getRandom().nextBoolean()) {
							Barang.beri(p, new ItemStack(LangitItems.KRISTAL_GERHANA));
							Msg.chat(p, Component.translatable("kultivator.kota_besar.pengamat.gerhana").withStyle(ChatFormatting.DARK_RED));
						} else {
							Msg.chat(p, Component.translatable("kultivator.kota_besar.pengamat.gerhana_kosong").withStyle(ChatFormatting.GRAY));
						}
					}
					case TWIN_MOONS -> {
						Barang.beri(p, new ItemStack(LangitItems.EMBUN_ROH));
						Msg.chat(p, Component.translatable("kultivator.kota_besar.pengamat.kembar").withStyle(ChatFormatting.AQUA));
					}
				}
			}
		}
	}

	private MenaraPengamat() {
	}
}
