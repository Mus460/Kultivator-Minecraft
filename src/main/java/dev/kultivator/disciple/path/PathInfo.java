package dev.kultivator.disciple.path;

import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Technique;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.kehancuran.Kehancuran;
import dev.kultivator.kehancuran.Shapes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * The {@code path} compound of the disciple screen's details (a {@code DiscipleHooks.addInfo} provider): everything the
 * Jalan tab shows. Reason keys are translation keys ("" when allowed).
 */
final class PathInfo {

	static void compose(DiscipleEntity entity, ServerPlayer master, CompoundTag tag) {
		DiscipleData data = entity.data();
		CompoundTag path = new CompoundTag();
		CompoundTag saved = DisciplePaths.tag(data);
		int stars = DisciplePaths.stars(data);
		double trialFactor = Stars.trialFactor(data);

		// ---- stars and calamities
		path.putInt("stars", stars);
		path.putBoolean("max_rank", data.rank().isMax());
		path.putDouble("stored", data.storedQi());
		path.putDouble("cost", Stars.cost(stars + 1));
		path.putBoolean("auto", saved.getBooleanOr("auto", true));
		String condense = Stars.condenseBlocker(entity);
		path.putBoolean("can_condense", condense == null);
		path.putString("condense_reason", condense == null ? "" : condense);
		int pending = Stars.pendingCalamity(data);
		int passed = DisciplePaths.calamitiesPassed(data);
		path.putInt("pending_k", pending);
		path.putInt("calamity", passed);
		path.putInt("first_gate", Stars.firstGate(data));
		path.putInt("gate", passed >= Calamity.COUNT ? 0 : Stars.gateStar(data, passed + 1));
		String calamity = Calamity.blocker(entity, master, pending, false, false);
		path.putBoolean("calamity_ok", calamity == null && pending > 0);
		path.putString("calamity_reason", calamity == null ? pending > 0 ? "" : DisciplePaths.PREFIX + "why.no_calamity" : calamity);
		path.putBoolean("running", Calamity.running(entity));
		path.putInt("trials", saved.getIntOr("trials", 0));
		path.putDouble("trial_factor", trialFactor);
		path.putDouble("technique_factor", Stars.techniqueFactor(stars, trialFactor));
		path.putDouble("melee_factor", Stars.meleeFactor(stars));
		path.putDouble("health_bonus", Stars.HEALTH_PER_STAR * stars * trialFactor * (Stars.ancientBlood(data) ? Stars.ANCIENT_BLOOD_HEALTH : 1.0D));
		path.putDouble("max_qi_factor", Stars.maxQiFactor(stars));
		path.putDouble("meditation_factor", 1.0D + Stars.MEDITATION_PER_STAR * stars);
		path.putBoolean("night", Stars.nightSky(entity));
		path.putDouble("reduction", Stars.overflowReduction(entity));
		path.putBoolean("trampling", passed >= Calamity.COUNT);
		path.putInt("lamps_lit", SoulLamps.lit(data));
		path.putInt("lamps_out", SoulLamps.out(data));
		String lamp = SoulLamps.blocker(data);
		path.putBoolean("lamp_ok", lamp == null);
		path.putString("lamp_reason", lamp == null ? "" : lamp);
		path.putInt("clones_allowed", DivineClones.allowed(stars));
		path.putInt("clone_cd", DivineClones.cooldownSeconds(entity));
		path.putInt("clone_active", DivineClones.active(entity));

		// ---- bond, seal, Hu Fa, foundation
		path.putInt("bond", DisciplePaths.bond(data));
		path.putBoolean("sealed", SoulSeal.sealed(data));
		path.putInt("seal_count", SoulSeal.sealedCount(master));
		path.putInt("seal_max", SoulSeal.maxSealed(master));
		String seal = SoulSeal.blocker(master, entity);
		path.putString("seal_reason", seal == null ? "" : seal);
		path.putFloat("seal_progress", SoulSeal.ritualProgress(entity));
		ListTag sealedList = new ListTag();
		long now = DisciplePaths.now(master.level().getServer());
		for (DiscipleEntity other : Disciples.loaded(master.getUUID())) {
			if (!SoulSeal.sealed(other.data()) || other.level() != master.level()) {
				continue;
			}
			CompoundTag entry = new CompoundTag();
			entry.putInt("id", other.getId());
			entry.putString("name", other.getName().getString());
			entry.putInt("dist", (int) Math.round(Math.sqrt(other.distanceToSqr(master))));
			long ready = DisciplePaths.tag(other.data()).getCompoundOrEmpty("cd").getLongOr("recall", 0L);
			entry.putInt("cd", ready <= now ? 0 : (int) ((ready - now + 19L) / 20L));
			sealedList.add(entry);
		}
		path.put("sealed_list", sealedList);
		HuFa.Guard guard = HuFa.guard(entity);
		path.putBoolean("trial", guard != null);
		path.putBoolean("trial_strikes", guard != null && guard.hasStrikes());
		path.putBoolean("hufa", HuFa.active(entity));
		path.putInt("tahan_left", HuFa.tahanLeft(entity));
		path.putBoolean("tahan_pending", HuFa.tahanPending(entity));
		int foundation = FoundationTypes.type(data);
		path.putInt("foundation", foundation);
		path.putInt("pillars", FoundationTypes.PILLARS[foundation]);

		// ---- heir, ring, surpassing
		boolean heir = DisciplePaths.isHeir(data);
		path.putBoolean("heir", heir);
		String heirWhy = Heir.blocker(master, entity);
		path.putString("heir_reason", heirWhy == null ? "" : heirWhy);
		String heirSoul = Heir.heirSoul(master);
		path.putString("heir_other", heirSoul.isEmpty() || heirSoul.equals(entity.soulId().toString()) ? "" : nameOf(master, heirSoul));
		path.putFloat("heir_progress", Heir.ritualProgress(entity));
		path.putInt("generation", Heir.generation(master));
		path.putInt("heir_gen", Heir.heirTag(data).getIntOr("gen", 0));
		ListTag hexes = new ListTag();
		List<String> hexIds = Heir.hexes(data);
		for (String id : hexIds) {
			hexes.add(StringTag.valueOf(id));
		}
		path.put("hexes", hexes);
		path.putString("gen_hex", Heir.generationHex(data));
		path.putInt("hex_pending", heir ? Heir.pending(data) : 0);
		ListTag hexOptions = new ListTag();
		ListTag genOptions = new ListTag();
		if (heir) {
			for (Technique technique : data.learnedTechniques()) {
				if (!hexIds.contains(technique.id())) {
					hexOptions.add(StringTag.valueOf(technique.id()));
				}
				if (Cultivators.get(master).knows(technique)) {
					genOptions.add(StringTag.valueOf(technique.id()));
				}
			}
		}
		path.put("hex_options", hexOptions);
		path.put("gen_options", genOptions);
		path.putBoolean("ring", MasterRing.has(data));
		path.putInt("ring_stage", MasterRing.ring(data).getIntOr("stage", 0));
		path.putInt("ring_left", MasterRing.awakenSecondsLeft(entity));
		path.putBoolean("descend", MasterRing.descending(entity));
		path.putInt("descend_cd", MasterRing.descendSecondsLeft(entity));
		String descend = MasterRing.bonded(data) ? MasterRing.blocker(master, entity) : DisciplePaths.PREFIX + "why.ring_not_bonded";
		path.putString("descend_reason", descend == null ? "" : descend);
		path.putBoolean("surpassed", Surpass.surpassed(data));
		path.putInt("power", DisciplePaths.power(data));
		path.putInt("master_power", Surpass.masterPower(master));
		path.putBoolean("honor", saved.getBooleanOr("honor", false));
		path.putBoolean("duel", DuelRules.partnerOf(entity) == master);
		String duel = Surpass.duelBlocker(master, entity);
		path.putString("duel_reason", duel == null ? "" : duel);

		// ---- Jurus Pemusnah and the red seal
		ListTag arts = new ListTag();
		for (DestroyerArts.Art art : DestroyerArts.available(data)) {
			arts.add(StringTag.valueOf(art.id()));
		}
		path.put("arts", arts);
		DestroyerArts.Art chosen = DestroyerArts.chosen(data);
		path.putString("art", chosen == null ? "" : chosen.id());
		path.putBoolean("art_eligible", DestroyerArts.eligible(data));
		path.putInt("art_cd", DestroyerArts.cooldownSeconds(entity));
		if (chosen != null) {
			Shapes.Spec spec = DestroyerArts.spec(chosen, stars);
			path.putString("art_kind", spec.kind().id());
			path.putInt("art_radius", spec.radius());
			path.putInt("art_depth", spec.depth());
			path.putInt("art_length", spec.length());
		}
		Kehancuran.discipleInfo(entity, master, path);
		if (entity.level() instanceof ServerLevel) {
			tag.put(DisciplePaths.EXTRA, path);
		}
	}

	/** A disciple's name from the master's list, or "?". */
	private static String nameOf(ServerPlayer master, String soul) {
		CompoundTag root = Cultivators.get(master).extra().getCompoundOrEmpty(Disciples.EXTRA);
		for (Tag tag : root.getListOrEmpty("list")) {
			if (tag instanceof CompoundTag entry && soul.equals(entry.getStringOr("id", ""))) {
				return entry.read("name", ComponentSerialization.CODEC).map(component -> component.getString()).orElse("?");
			}
		}
		return "?";
	}

	private PathInfo() {
	}
}
