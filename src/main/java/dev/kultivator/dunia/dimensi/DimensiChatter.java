package dev.kultivator.dunia.dimensi;

import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * The small talk of module D's people (DESIGN 34.5.13-14 "dialogue variety"): when a player stands within 6 b, an NPC
 * now and then speaks one line on the player's action bar, {@code "<name>: «line»"}. Lines live in
 * {@code lang-src/dunia_dimensi.*} under {@code kultivator.dimensi.celoteh.<key>.<line>}:
 * <ul>
 * <li>{@code g0}-{@code g3}: the role's everyday lines (Meng Po's soup, the judges' ledgers, the fisherman's nets);</li>
 * <li>{@code r0} / {@code r1} / {@code r2}: what it says to a young soul (below Dao Union), to a great one (Dao Union,
 * Immortal Ascension) and to a True Immortal;</li>
 * <li>{@code e}: what it says while a sky event holds the overworld (Dunia Bawah: only the blood eclipse, whose gates
 * stand open; the sea: any of the three).</li>
 * </ul>
 * Rate: one line per player every 8 s at most, the same NPC to the same player every 40 s at most; a line now and then
 * carries the role's voice (the voice API rate-limits it again). Nothing here changes gameplay.
 */
public final class DimensiChatter {

	public static final int GENERAL = 4;
	public static final int PLAYER_GAP = 160;
	public static final int PAIR_GAP = 800;
	public static final int BAWAH_COLOR = 0xC9B8FF;
	public static final int LAUT_COLOR = 0x9FE8F0;

	/** True for a True Immortal (officials bow, the lines change). */
	public static boolean immortal(ServerPlayer player) {
		return Cultivators.get(player).realm().atLeast(Realm.TRUE_IMMORTAL);
	}

	/** 0 young (below Dao Union), 1 great (Dao Union, Immortal Ascension), 2 True Immortal. */
	public static int tier(ServerPlayer player) {
		Realm realm = Cultivators.get(player).realm();
		if (realm.atLeast(Realm.TRUE_IMMORTAL)) {
			return 2;
		}
		return realm.atLeast(Realm.DAO_UNION) ? 1 : 0;
	}

	/** Maybe speak one line to {@code player} (from the NPC's once-a-second ambient pass). */
	static void consider(ServerLevel level, DimensiNpc npc, ServerPlayer player) {
		String key = npc.chatterKey();
		if (key == null || !Voice.isReady(player.getUUID(), "dimensi_celoteh")
				|| !Voice.isReady(player.getUUID(), "dimensi_celoteh|" + npc.getUUID())) {
			return;
		}
		RandomSource random = npc.getRandom();
		if (random.nextInt(3) != 0) {
			return;
		}
		Voice.ready(player.getUUID(), "dimensi_celoteh", PLAYER_GAP);
		Voice.ready(player.getUUID(), "dimensi_celoteh|" + npc.getUUID(), PAIR_GAP);
		say(npc, player, line(npc, player, random));
	}

	/** Speaks {@code line} (a full lang key) to {@code player}: the action bar, the gesture, sometimes the voice. */
	public static void say(DimensiNpc npc, ServerPlayer player, String line) {
		int color = npc.ofTheSea() ? LAUT_COLOR : BAWAH_COLOR;
		Msg.actionBar(player, Component.translatable("kultivator.dimensi.celoteh.format", npc.getName(), Component.translatable(line))
				.withColor(color));
		npc.talk(50);
		npc.getLookControl().setLookAt(player, 30.0F, 30.0F);
		if (npc.getRandom().nextInt(4) == 0 && Voice.free(npc)) {
			voice(npc);
		}
	}

	/** The line key for this player now. */
	static String line(DimensiNpc npc, ServerPlayer player, RandomSource random) {
		String base = "kultivator.dimensi.celoteh." + npc.chatterKey() + ".";
		boolean event = npc.ofTheSea() ? DimensiSky.active(player.level().getServer()) != null : DimensiSky.eclipse(player.level().getServer());
		if (event && random.nextBoolean()) {
			return base + "e";
		}
		if (random.nextInt(100) < 35) {
			return base + "r" + tier(player);
		}
		return base + "g" + random.nextInt(GENERAL);
	}

	/** The role's voice, the phase-34 line first (module D's caption on the reused fallback). */
	private static void voice(DimensiNpc npc) {
		String key = npc.chatterKey();
		if (key == null) {
			return;
		}
		switch (key) {
			case "meng_po" -> DimensiVoice.say(npc, DimensiVoice.MENG_PO_OFFER, "disciple.f3.greet", "kultivator.dimensi.voice.meng_po_offer");
			case "penjaga_alam" -> DimensiVoice.say(npc, DimensiVoice.OX_HEAD, "disciple.m1.careful", "kultivator.dimensi.voice.ox_head");
			case "arwah" -> DimensiVoice.say(npc, DimensiVoice.GHOST_PLEA, "disciple.f2.careful", "kultivator.dimensi.voice.ghost_plea");
			case "hakim", "yama" -> DimensiVoice.say(npc, DimensiVoice.YAMA_JUDGEMENT, "boss.taunt", "kultivator.dimensi.voice.yama_judgement");
			case "raja_naga" -> DimensiVoice.say(npc, DimensiVoice.DRAGON_KING, "boss.taunt", "kultivator.dimensi.voice.dragon_king");
			case "penjaga_laut" -> DimensiVoice.say(npc, DimensiVoice.GATE_GUARD, "disciple.m1.greet", "kultivator.dimensi.voice.gate_guard");
			case "pertapa", "dewa_penglai" -> DimensiVoice.sayReused(npc, "elder.advice", null);
			case "wuchang_putih" -> DimensiVoice.sayReused(npc, "disciple.m2.greet_alt", null);
			case "wuchang_hitam" -> DimensiVoice.sayReused(npc, "disciple.m3.careful", null);
			case "nelayan" -> DimensiVoice.sayReused(npc, "disciple.m3.greet", null);
			case "pedagang" -> DimensiVoice.sayReused(npc, "disciple.m2.greet", null);
			default -> {
			}
		}
	}

	private DimensiChatter() {
	}
}
