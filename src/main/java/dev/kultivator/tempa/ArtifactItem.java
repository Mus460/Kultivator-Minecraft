package dev.kultivator.tempa;

import dev.kultivator.data.Element;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A forged artifact, {@code kultivator:forged_artifact} with the {@code kultivator:artifact} component (DESIGN 25.5.4-25.5.5,
 * 25.15, owned by P8-tempa). The record holds {form, grade 0-5, sub 0-2, element, refine 0-12, spirit {stage, years, kind,
 * seeded}, maker, bound, bound_name, id}; the stack also carries the model string and tint ({@code custom_model_data}) and,
 * for a Pedang or a Wajan, its attribute modifiers, all rewritten by {@link #write}. Sumber: RMJ ch. 415, 928, 1237, 1393;
 * AWE ch. 5, 29-38; the four forms of DESIGN 20.3.17. Every number is game design.
 */
public class ArtifactItem extends Item {

	/** Forms. */
	public static final String PEDANG = "pedang";
	public static final String PAYUNG = "payung";
	public static final String WAJAN = "wajan";
	public static final String LONCENG = "lonceng";
	public static final List<String> FORMS = List.of(PEDANG, PAYUNG, WAJAN, LONCENG);

	public static final int MAX_GRADE = 5;
	public static final int MAX_SUB = 2;
	public static final int MAX_REFINE = 12;
	/** AP base per grade Kuning … Dewa. */
	public static final double[] BASE = {1.0D, 1.6D, 2.5D, 4.0D, 6.0D, 9.0D};
	public static final double SUB_STEP = 0.15D;
	public static final double REFINE_STEP = 0.08D;
	public static final double NATAL_FACTOR = 1.25D;
	/** AP factor per spirit stage 0-3. */
	public static final double[] SPIRIT_FACTOR = {1.0D, 1.1D, 1.25D, 1.5D};
	public static final double SWORD_BASE = 6.0D;
	public static final double SWORD_PER_AP = 0.6D;
	public static final double SWORD_CAP = 22.0D;
	public static final double SWORD_SPEED = 1.6D;
	public static final double WAJAN_KNOCKBACK_PER_GRADE = 0.05D;
	/** Grade colours Kuning … Dewa (ribbons, tooltip). */
	public static final int[] GRADE_COLORS = {0xE8C547, 0x9A7CFF, 0xC08A3E, 0x6FD3FF, 0xFFE9A8, 0xFF6AD5};
	/** Refinement tints: silver at +3, gold at +6, azure at +9, white flame at +12 (DESIGN 25.5.8). */
	public static final int TINT_SILVER = 0xD8E0EA;
	public static final int TINT_GOLD = 0xFFD54A;
	public static final int TINT_AZURE = 0x5AC8FF;
	public static final int TINT_WHITE = 0xFFFFFF;
	public static final int TINT_PLAIN = 0xB08A5A;

	public ArtifactItem(Properties properties) {
		super(properties);
	}

	// ================================================================== record reads

	/** True for a forged artifact with a record. */
	public static boolean is(ItemStack stack) {
		return stack != null && !stack.isEmpty() && stack.getItem() instanceof ArtifactItem && stack.has(TempaItems.ARTIFACT);
	}

	/** A copy of the stack's record (empty when it has none). */
	public static CompoundTag data(ItemStack stack) {
		CompoundTag tag = stack == null ? null : stack.get(TempaItems.ARTIFACT);
		return tag == null ? new CompoundTag() : tag.copy();
	}

	public static String form(CompoundTag tag) {
		String form = tag.getStringOr("form", PEDANG);
		return FORMS.contains(form) ? form : PEDANG;
	}

	public static int formIndex(String form) {
		return Math.max(0, FORMS.indexOf(form));
	}

	public static int grade(CompoundTag tag) {
		return Math.max(0, Math.min(MAX_GRADE, tag.getIntOr("grade", 0)));
	}

	public static int sub(CompoundTag tag) {
		return Math.max(0, Math.min(MAX_SUB, tag.getIntOr("sub", 0)));
	}

	public static int refine(CompoundTag tag) {
		return Math.max(0, Math.min(MAX_REFINE, tag.getIntOr("refine", 0)));
	}

	public static @Nullable Element element(CompoundTag tag) {
		String id = tag.getStringOr("element", "");
		return id.isEmpty() ? null : Element.byId(id);
	}

	public static CompoundTag spirit(CompoundTag tag) {
		return tag.getCompoundOrEmpty("spirit");
	}

	public static int spiritStage(CompoundTag tag) {
		return Math.max(0, Math.min(3, spirit(tag).getIntOr("stage", 0)));
	}

	public static double spiritYears(CompoundTag tag) {
		return Math.max(0.0D, spirit(tag).getDoubleOr("years", 0.0D));
	}

	/** 0 natural spirit, 1 Roh Buatan (artificial, capped at stage 2). */
	public static int spiritKind(CompoundTag tag) {
		return spirit(tag).getIntOr("kind", 0) == 1 ? 1 : 0;
	}

	public static @Nullable UUID bound(CompoundTag tag) {
		return parse(tag.getStringOr("bound", ""));
	}

	public static @Nullable UUID id(CompoundTag tag) {
		return parse(tag.getStringOr("id", ""));
	}

	static @Nullable UUID parse(String text) {
		if (text == null || text.isEmpty()) {
			return null;
		}
		try {
			return UUID.fromString(text);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	// ================================================================== power

	/** The artifact power AP of DESIGN 25.5.5 (0 for anything that is not an artifact); the natal factor is not included. */
	public static double power(ItemStack stack) {
		return is(stack) ? powerOf(data(stack)) : 0.0D;
	}

	/** AP of a record: {@code base[grade] × (1 + 0.15 sub) × (1 + 0.08 refine) × spirit}. */
	public static double powerOf(CompoundTag tag) {
		return BASE[grade(tag)] * (1.0D + SUB_STEP * sub(tag)) * (1.0D + REFINE_STEP * refine(tag)) * SPIRIT_FACTOR[spiritStage(tag)];
	}

	/** A Pedang's attack damage for {@code ap}: {@code 6 + 0.6 AP}, at most 22. */
	public static double swordDamage(double ap) {
		return Math.min(SWORD_CAP, SWORD_BASE + SWORD_PER_AP * ap);
	}

	// ================================================================== creation

	/** A new artifact of {@code form} (pedang, payung, wajan, lonceng), {@code grade} 0-5, {@code sub} 0-2 and {@code refine} 0-12. */
	public static ItemStack create(String form, int grade, int sub, int refine) {
		return create(form, grade, sub, refine, null, null);
	}

	/** A new artifact with an element (null: none) and a maker's name (null: none) and a fresh id. */
	public static ItemStack create(String form, int grade, int sub, int refine, @Nullable Element element, @Nullable String maker) {
		CompoundTag tag = new CompoundTag();
		tag.putString("form", FORMS.contains(form) ? form : PEDANG);
		tag.putInt("grade", Math.max(0, Math.min(MAX_GRADE, grade)));
		tag.putInt("sub", Math.max(0, Math.min(MAX_SUB, sub)));
		tag.putInt("refine", Math.max(0, Math.min(MAX_REFINE, refine)));
		tag.putString("element", element == null ? "" : element.id());
		if (maker != null && !maker.isEmpty()) {
			tag.putString("maker", maker);
		}
		tag.putString("id", UUID.randomUUID().toString());
		return fromData(tag);
	}

	/** A stack carrying {@code tag} (copied). */
	public static ItemStack fromData(CompoundTag tag) {
		ItemStack stack = new ItemStack(TempaItems.FORGED_ARTIFACT);
		write(stack, tag);
		return stack;
	}

	/** Writes the record onto the stack and refreshes its model string, tint and attribute modifiers. */
	public static void write(ItemStack stack, CompoundTag source) {
		CompoundTag tag = source.copy();
		if (tag.getStringOr("id", "").isEmpty()) {
			tag.putString("id", UUID.randomUUID().toString());
		}
		stack.set(TempaItems.ARTIFACT, tag);
		String form = form(tag);
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(form), List.of(tint(tag))));
		double ap = powerOf(tag);
		if (PEDANG.equals(form)) {
			stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
					.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, swordDamage(ap) - 1.0D,
							AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
					.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, SWORD_SPEED - 4.0D,
							AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
					.build());
		} else if (WAJAN.equals(form)) {
			stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
					.add(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(dev.kultivator.Kultivator.id("artifact_wajan"),
							WAJAN_KNOCKBACK_PER_GRADE * (grade(tag) + 1), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.OFFHAND)
					.build());
		} else {
			stack.remove(DataComponents.ATTRIBUTE_MODIFIERS);
		}
	}

	/** The accent tint: the refinement tint from +3, else the element colour (bronze without one). */
	public static int tint(CompoundTag tag) {
		int refine = refine(tag);
		if (refine >= 12) {
			return TINT_WHITE;
		}
		if (refine >= 9) {
			return TINT_AZURE;
		}
		if (refine >= 6) {
			return TINT_GOLD;
		}
		if (refine >= 3) {
			return TINT_SILVER;
		}
		Element element = element(tag);
		return element == null ? TINT_PLAIN : element.color();
	}

	// ================================================================== text

	public static Component formName(String form) {
		return Component.translatable("kultivator.tempa.form." + form);
	}

	public static Component gradeName(int grade) {
		return Component.translatable("kultivator.tempa.grade." + Math.max(0, Math.min(MAX_GRADE, grade)))
				.withColor(GRADE_COLORS[Math.max(0, Math.min(MAX_GRADE, grade))]);
	}

	public static Component subName(int sub) {
		return Component.translatable("kultivator.tempa.sub." + Math.max(0, Math.min(MAX_SUB, sub)));
	}

	public static Component spiritName(int stage) {
		return Component.translatable("kultivator.tempa.spirit." + Math.max(0, Math.min(3, stage)));
	}

	/** "Pedang Pusaka Api · Langit Tinggi +0". */
	public static MutableComponent displayName(CompoundTag tag) {
		Element element = element(tag);
		Component elementPart = element == null ? Component.empty() : Component.translatable("kultivator.tempa.name.element", element.displayName());
		return Component.translatable("kultivator.tempa.name", formName(form(tag)), elementPart,
				Component.translatable("kultivator.tempa.grade." + grade(tag)), subName(sub(tag)), refine(tag));
	}

	@Override
	public Component getName(ItemStack stack) {
		if (!stack.has(TempaItems.ARTIFACT)) {
			return super.getName(stack);
		}
		CompoundTag tag = data(stack);
		return displayName(tag).withColor(GRADE_COLORS[grade(tag)]);
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		CompoundTag tag = data(stack);
		return refine(tag) >= 9 || spiritStage(tag) >= 2 || super.isFoil(stack);
	}

	@Override
	public boolean canFitInsideContainerItems() {
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		if (!stack.has(TempaItems.ARTIFACT)) {
			return;
		}
		CompoundTag tag = data(stack);
		String form = form(tag);
		double ap = powerOf(tag);
		int grade = grade(tag);
		builder.accept(Component.translatable("kultivator.tempa.tip.grade", gradeName(grade), subName(sub(tag)), refine(tag))
				.withStyle(ChatFormatting.GRAY));
		Element element = element(tag);
		if (element != null) {
			builder.accept(Component.translatable("kultivator.tempa.tip.element", element.displayName()).withColor(element.color()));
		}
		builder.accept(Component.translatable("kultivator.tempa.tip.ap", fmt(ap), fmt(ap * NATAL_FACTOR)).withStyle(ChatFormatting.AQUA));
		builder.accept(effectLine(form, grade, ap).withStyle(ChatFormatting.DARK_AQUA));
		int stage = spiritStage(tag);
		double years = spiritYears(tag);
		if (stage > 0 || years > 0.0D) {
			MutableComponent line = Component.translatable("kultivator.tempa.tip.spirit", spiritName(stage), (int) Math.floor(years));
			if (spiritKind(tag) == 1) {
				line.append(Component.translatable("kultivator.tempa.tip.artificial"));
			}
			builder.accept(line.withStyle(ChatFormatting.LIGHT_PURPLE));
			if (stage >= 3) {
				builder.accept(Component.translatable("kultivator.tempa.tip.epithet." + form).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
			}
		}
		String boundName = tag.getStringOr("bound_name", "");
		if (bound(tag) != null) {
			builder.accept(Component.translatable("kultivator.tempa.tip.bound", boundName.isEmpty() ? "?" : boundName).withStyle(ChatFormatting.GOLD));
		} else {
			builder.accept(Component.translatable("kultivator.tempa.tip.unbound").withStyle(ChatFormatting.DARK_GRAY));
		}
		String maker = tag.getStringOr("maker", "");
		if (!maker.isEmpty()) {
			builder.accept(Component.translatable("kultivator.tempa.tip.maker", maker).withStyle(ChatFormatting.DARK_GRAY));
		}
		builder.accept(Component.translatable("item.kultivator.forged_artifact.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		builder.accept(Component.translatable("kultivator.tempa.grade." + grade + ".source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		if (stage > 0) {
			builder.accept(Component.translatable("kultivator.tempa.spirit." + stage + ".source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		}
	}

	/** The form's effect with the real numbers (DESIGN 25.5.5). */
	public static MutableComponent effectLine(String form, int grade, double ap) {
		return switch (form) {
			case PAYUNG -> Component.translatable("kultivator.tempa.effect.payung", ArtifactEffects.payungInterval(grade),
					ArtifactEffects.payungAmplifier(grade) + 1);
			case WAJAN -> Component.translatable("kultivator.tempa.effect.wajan", pct(ArtifactEffects.wajanReduction(ap)),
					pct(WAJAN_KNOCKBACK_PER_GRADE * (grade + 1)));
			case LONCENG -> Component.translatable(grade >= 4 ? "kultivator.tempa.effect.lonceng_weak" : "kultivator.tempa.effect.lonceng",
					(int) ArtifactEffects.loncengRadius(grade));
			default -> Component.translatable("kultivator.tempa.effect.pedang", fmt(swordDamage(ap)), fmt(SWORD_SPEED),
					pct(ArtifactEffects.SWORD_DAO_PER_AP * ap));
		};
	}

	public static String fmt(double value) {
		return String.format(Locale.ROOT, "%.1f", value);
	}

	public static String pct(double fraction) {
		return String.format(Locale.ROOT, "%.0f%%", fraction * 100.0D);
	}
}
