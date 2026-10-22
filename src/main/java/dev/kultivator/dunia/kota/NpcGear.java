package dev.kultivator.dunia.kota;

import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.duel.clash.WeaponClass;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * What the NPCs carry (DESIGN 34.5.1-34.5.4, 35.7.6): the guard's spear (always in hand, the town watch), the smith's
 * hammer (a mace, always in hand at the forge), the wanderer's and the rival's blade (in its sheath until it fights or
 * duels: {@link KultivatorNpc#arm}). The blade follows the realm (iron, then diamond from Nascent Soul, netherite from
 * Immortal Ascension); the Klan Tie of the north, body cultivators, swing axes. Stacks are made when an NPC draws, never
 * in a static initializer, and carry no attribute modifiers, reach, lunge or shield-breaking components: the combat
 * numbers stay those of DESIGN 25.4.2.
 */
final class NpcGear {

	/** Roles whose weapon is always in hand. */
	static boolean alwaysArmed(NpcRole role) {
		return role == NpcRole.PENJAGA || role == NpcRole.PANDAI_PUSAKA;
	}

	/** Roles that carry a blade at all (the others never draw). */
	static boolean armed(NpcRole role) {
		return alwaysArmed(role) || role == NpcRole.PENGEMBARA || role == NpcRole.SAINGAN;
	}

	/** True when the NPC's blade is an axe (rival body cultivators, style "tubuh"). */
	static boolean axe(KultivatorNpc npc) {
		if (npc.role() != NpcRole.SAINGAN || npc.group().isEmpty() || !(npc.level() instanceof net.minecraft.server.level.ServerLevel level)) {
			return false;
		}
		SainganData.Group group = SainganData.get(level.getServer()).byId(npc.group());
		return group != null && "tubuh".equals(SainganData.STYLES[Math.floorMod(group.style, SainganData.STYLES.length)]);
	}

	/** The item the NPC holds when armed. */
	static Item item(KultivatorNpc npc) {
		NpcRole role = npc.role();
		if (role == NpcRole.PENJAGA) {
			return Items.IRON_SPEAR;
		}
		if (role == NpcRole.PANDAI_PUSAKA) {
			return Items.MACE;
		}
		int realm = npc.realm();
		if (axe(npc)) {
			return realm >= Realm.NASCENT_SOUL.ordinal() ? Items.DIAMOND_AXE : Items.IRON_AXE;
		}
		if (realm >= Realm.IMMORTAL_ASCENSION.ordinal()) {
			return Items.NETHERITE_SWORD;
		}
		if (realm >= Realm.NASCENT_SOUL.ordinal()) {
			return Items.DIAMOND_SWORD;
		}
		// a Fire Dao wanderer's blade glints gold
		return npc.dao() == Dao.FIRE && realm >= Realm.GOLDEN_CORE.ordinal() ? Items.GOLDEN_SWORD : Items.IRON_SWORD;
	}

	/** A fresh weapon stack without attribute modifiers. */
	static ItemStack weapon(KultivatorNpc npc) {
		ItemStack stack = new ItemStack(item(npc));
		stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		// nor the reach, the spear's lunge or the axe's shield-breaking: the NPC fights exactly as before, only armed
		stack.remove(DataComponents.ATTACK_RANGE);
		stack.remove(DataComponents.PIERCING_WEAPON);
		stack.remove(DataComponents.KINETIC_WEAPON);
		stack.remove(DataComponents.WEAPON);
		return stack;
	}

	/** The duel weapon class of the NPC (what it draws), the same as {@code WeaponClass.of} of its stack. */
	static WeaponClass duelWeapon(KultivatorNpc npc) {
		NpcRole role = npc.role();
		if (role == NpcRole.PENJAGA) {
			return WeaponClass.TOMBAK;
		}
		if (role == NpcRole.PANDAI_PUSAKA) {
			return WeaponClass.GADA;
		}
		return axe(npc) ? WeaponClass.KAPAK : WeaponClass.PEDANG;
	}

	private NpcGear() {
	}
}
