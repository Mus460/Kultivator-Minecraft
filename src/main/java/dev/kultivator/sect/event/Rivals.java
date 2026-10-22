package dev.kultivator.sect.event;

import dev.kultivator.data.BreakthroughQuality;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The visitors of the tournaments (DESIGN 20.3.12): Murid Tepi Utara (north-bank disciples) filling empty bracket slots,
 * rolled at the entrants' median rank plus or minus one stage, and the visiting champions of rival sects in a Turnamen
 * Elit. A visitor is a body of role RIVAL spawned only for its own bout (never saved) and fights only its duel partner.
 * <p>
 * Sumber: A Will Eternal (the Outer Sect Chosen battles, the north bank takes most of the top ten); Against the Gods
 * ch. 434-436 and 1359 (ranking tournaments against rival nations' geniuses).
 */
final class Rivals {

	/** Rival names (both languages) in {@code kultivator.sectevent.rival.<n>}, rival sects in {@code rival_sect.<n>}. */
	public static final int NAME_COUNT = 24;
	public static final int SECT_COUNT = 8;

	/** A north-bank disciple at rank step {@code step}. */
	static Bracket.Entrant rival(RandomSource random, int step) {
		DiscipleData data = body(random, step);
		Component name = Component.translatable("kultivator.sectevent.rival.title",
				Component.translatable("kultivator.sectevent.rival." + random.nextInt(NAME_COUNT)));
		return new Bracket.Entrant(Bracket.RIVAL, null, name, data, random.nextInt(DiscipleEntity.SKIN_COUNT), random.nextBoolean(),
				data.rank().step());
	}

	/** A rival sect's visiting champion at rank step {@code step}. */
	static Bracket.Entrant champion(RandomSource random, int step, int sect) {
		return champion(random, step, Component.translatable("kultivator.sectevent.rival_sect." + Math.floorMod(sect, SECT_COUNT)));
	}

	/** A champion of the named sect (T12: an NPC sect of Diplomasi the hall already met). */
	static Bracket.Entrant champion(RandomSource random, int step, Component sectName) {
		DiscipleData data = body(random, step);
		data.setComprehension(20.0D + random.nextInt(40));
		Component name = Component.translatable("kultivator.sectevent.champion.name",
				Component.translatable("kultivator.sectevent.rival." + random.nextInt(NAME_COUNT)), sectName);
		return new Bracket.Entrant(Bracket.CHAMPION, null, name, data, random.nextInt(DiscipleEntity.SKIN_COUNT), random.nextBoolean(),
				data.rank().step());
	}

	/** A cultivator's data at {@code step}: a rolled root, Normal qualities up to its realm, full qi. */
	static DiscipleData body(RandomSource random, int step) {
		DiscipleData data = DiscipleData.fresh(random);
		data.setRank(Rank.ofStep(Math.max(0, Math.min(Rank.MAX_STEP, step))));
		for (Realm realm : Realm.values()) {
			if (!realm.isMortal() && realm.ordinal() <= data.realm().ordinal()) {
				data.setQuality(realm, BreakthroughQuality.NORMAL);
			}
		}
		data.setComprehension(random.nextInt(30));
		data.setQi(DiscipleCultivation.maxQi(data));
		return data;
	}

	/** Spawns a visitor's body (role RIVAL, or CANDIDATE for a war youth) at {@code pos}; null when it could not. */
	static @Nullable DiscipleEntity spawn(ServerLevel level, Vec3 pos, Bracket.Entrant entrant, BlockPos ring) {
		DiscipleEntity.Role role = entrant.kind == Bracket.YOUTH ? DiscipleEntity.Role.CANDIDATE : DiscipleEntity.Role.RIVAL;
		DiscipleEntity body = Disciples.spawnUnowned(level, pos, entrant.data.copy(), entrant.name, entrant.skin, entrant.slim, role, ring);
		if (body != null) {
			body.setHealth(body.getMaxHealth());
		}
		return body;
	}

	private Rivals() {
	}
}
