package dev.kultivator.dunia.kota;

import dev.kultivator.data.Cultivators;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerPlayer;

/**
 * The town screen's data for one player and one NPC (DESIGN 34.5.1): the town's name, the player's Nama Baik, stones and
 * discount, and the tabs. The auction, the teahouse and the notice board are the town's and shown by every NPC; the
 * shop and the black market belong to their merchant and are sent only by them (the other tabs then point the way).
 */
final class KotaView {

	static CompoundTag town(ServerPlayer player, KultivatorNpc npc) {
		BalaiLelang.payOwed(player);
		CompoundTag tag = new CompoundTag();
		tag.store("town", ComponentSerialization.CODEC, KotaNames.town(npc.town()));
		tag.putInt("role", npc.role().ordinal());
		tag.putInt("rep", KotaData.get(player.level().getServer()).reputation(npc.town(), player.getUUID()));
		tag.putLong("nb", NilaiBatu.total(player));
		tag.putInt("realm", Cultivators.get(player).realm().ordinal());
		tag.putBoolean("discount", Toko.discount(player, npc) < 1.0D);
		tag.putBoolean("raided", PasarGelap.raided(player, npc.town()));
		if (npc.role() == NpcRole.PEDAGANG_PIL || npc.role() == NpcRole.PANDAI_PUSAKA) {
			tag.put("toko", Toko.view(player, npc));
		}
		if (npc.role() == NpcRole.MAKELAR_GELAP) {
			tag.put("gelap", PasarGelap.view(player, npc));
		}
		tag.put("lelang", BalaiLelang.view(player, npc));
		tag.put("teh", KedaiTeh.view(player, npc));
		tag.put("misi", PapanMisi.view(player, npc));
		return tag;
	}

	private KotaView() {
	}
}
