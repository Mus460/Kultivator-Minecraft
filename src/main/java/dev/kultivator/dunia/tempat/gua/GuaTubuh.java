package dev.kultivator.dunia.tempat.gua;

import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * The people of the caves (DESIGN 38.C.4: Pertapa, Sisa Jiwa, Tetua Iblis, Tawanan, the peak's watching wanderers): bodies
 * only through {@code HidupApi.tubuh} (38NL, the NPC cap of {@code DuniaBudget}), their realm set from the place, their
 * clip through {@code HidupApi.klip} (40's {@code SosokApi}), and their chat lines. Every body is owned by the run that
 * made it and leaves with it.
 */
public final class GuaTubuh {

	/**
	 * A body of {@code role} at {@code pos} facing {@code yaw}, bound to the run's site, re-set to {@code realm} at
	 * {@code tier}, with {@code dao} (null keeps the hash pick), playing {@code klip} with {@code prop} (0 none) until
	 * replaced. Null when the NPC cap is full.
	 */
	public static @Nullable KultivatorNpc buat(GuaRun run, NpcRole role, BlockPos pos, float yaw, int tier, Realm realm, @Nullable Dao dao,
			int klip, int prop) {
		KultivatorNpc npc = HidupApi.tubuh(run.level, run.situs(), role, pos, yaw);
		if (npc == null) {
			return null;
		}
		npc.setup(role, tier, npc.look(), realm.ordinal(), Math.floorMod(npc.look() >>> 5, 4), dao, npc.voice(), npc.robe(), npc.plate());
		npc.setYRot(yaw);
		npc.setYHeadRot(yaw);
		npc.setYBodyRot(yaw);
		if (dao != null) {
			npc.memory().putString("dao", dao.id());
		}
		if (klip > 0) {
			HidupApi.klip(npc, klip, prop, 0);
		}
		run.own(npc);
		return npc;
	}

	/** A spoken line: {@code Name: text} in the speaker's colour (light purple for the dead, grey text). */
	public static void ucap(ServerPlayer player, Entity speaker, Component text, ChatFormatting colour) {
		Msg.chat(player, Component.empty().append(speaker.getDisplayName().copy().withStyle(colour))
				.append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY)).append(text.copy().withStyle(ChatFormatting.GRAY)));
	}

	private GuaTubuh() {
	}
}
