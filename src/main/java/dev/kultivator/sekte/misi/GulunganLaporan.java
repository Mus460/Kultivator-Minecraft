package dev.kultivator.sekte.misi;

import dev.kultivator.Kultivator;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Gulungan Laporan, the mission report scroll (DESIGN 36.2.7, module X): a rolled bamboo-paper scroll tied with a red
 * cord and stamped with the sect's seal, handed to the master when a story mission team is home. The report itself (the
 * team, the three stages with their branch, what was brought home, quoted memories) rides in {@code custom_data} under
 * {@link #KEY}; using the scroll sends it to the client ({@code MisiPayloads.Laporan}), which opens the report screen.
 * Only {@link #create} makes stacks (lazily, when a report is handed over), never a static initializer. Not in the
 * creative tab (an empty scroll says nothing).
 */
public final class GulunganLaporan extends Item {

	public static final String KEY = "kultivator_misi_laporan";
	private static Item item;

	private GulunganLaporan(Item.Properties properties) {
		super(properties);
	}

	/** Registers {@code kultivator:gulungan_laporan} (once, from {@code Misi.init}). */
	static void register() {
		if (item != null) {
			return;
		}
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Kultivator.id("gulungan_laporan"));
		item = Registry.register(BuiltInRegistries.ITEM, key,
				new GulunganLaporan(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON).setId(key)));
	}

	/** A scroll holding {@code report}, with two lore lines (the mission and its outcome). */
	static ItemStack create(CompoundTag report) {
		if (item == null) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = new ItemStack(item);
		CompoundTag custom = new CompoundTag();
		custom.put(KEY, report.copy());
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
		TemplatMisi tpl = TemplatMisi.byId(report.getStringOr("tpl", ""));
		List<Component> lore = new ArrayList<>();
		lore.add(Component.translatable("kultivator.sekte.misi.gulungan.lore", tpl == null ? Component.literal("?") : Component.translatable(tpl.key()),
				report.getIntOr("tier", 1)).withColor(0xE8D7A8));
		lore.add(Component.translatable("kultivator.sekte.misi.gulungan.lore2", Component.translatable("kultivator.sekte.misi.grade."
				+ report.getIntOr("grade", 0)), report.getLongOr("day", 0L)).withColor(0xB8A98A));
		lore.add(Component.translatable("kultivator.sekte.misi.gulungan.lore3").withColor(0x8A7F6A));
		stack.set(DataComponents.LORE, new ItemLore(lore));
		return stack;
	}

	/** The report inside {@code stack}, or an empty tag. */
	static CompoundTag report(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag().getCompoundOrEmpty(KEY);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
			CompoundTag report = report(player.getItemInHand(hand));
			if (!report.isEmpty()) {
				MisiPayloads.sendReport(serverPlayer, report);
			}
		}
		return InteractionResult.SUCCESS;
	}
}
