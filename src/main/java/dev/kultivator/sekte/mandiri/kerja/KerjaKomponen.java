package dev.kultivator.sekte.mandiri.kerja;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.kultivator.Kultivator;
import dev.kultivator.disciple.DiscipleEntity;
import net.fabricmc.fabric.api.item.v1.ItemComponentTooltipProviderRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The two item marks of Murid Mandiri (contract C39.D2, DESIGN 39.6.8, 39.6.11), small custom data components registered
 * always (rule K1) from {@code Kerja.registerContent}:
 * <ul>
 *   <li>{@code kultivator:buatan_murid} = {@link Karya}{@code (soul, nama)}: the disciple's own work (a pill it brewed, a
 *   talisman it wrote). Only these and loot may be sold by a disciple (39.6.8 "jual karya"). Tooltip: "Buatan
 *   &lt;nama&gt;".</li>
 *   <li>{@code kultivator:beli_murid} = soul UUID: something a disciple bought with its own purse (written by 39C); never
 *   sold again by a disciple (anti money loop). Tooltip: "Dibeli murid".</li>
 * </ul>
 * Both are saved and synced; the tooltips go through Fabric's common tooltip registry (shown on the client only).
 */
public final class KerjaKomponen {

	/** A disciple's own work: who made it (soul) and the name it had then. */
	public record Karya(UUID soul, String nama) {
		public static final Codec<Karya> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				UUIDUtil.CODEC.fieldOf("soul").forGetter(Karya::soul),
				Codec.STRING.optionalFieldOf("nama", "").forGetter(Karya::nama)
		).apply(instance, Karya::new));
		public static final StreamCodec<io.netty.buffer.ByteBuf, Karya> STREAM_CODEC = StreamCodec.composite(
				UUIDUtil.STREAM_CODEC, Karya::soul, ByteBufCodecs.stringUtf8(64), Karya::nama, Karya::new);

		public Karya {
			nama = nama == null ? "" : nama.length() > 64 ? nama.substring(0, 64) : nama;
		}
	}

	public static final DataComponentType<Karya> BUATAN_MURID = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Kultivator.id("buatan_murid"), DataComponentType.<Karya>builder()
					.persistent(Karya.CODEC)
					.networkSynchronized(Karya.STREAM_CODEC)
					.build());

	public static final DataComponentType<UUID> BELI_MURID = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Kultivator.id("beli_murid"), DataComponentType.<UUID>builder()
					.persistent(UUIDUtil.CODEC)
					.networkSynchronized(UUIDUtil.STREAM_CODEC)
					.build());

	private static boolean registered;

	/** Loads the class (the two types above) and adds their tooltip lines; safe to call twice. */
	static void register() {
		if (registered) {
			return;
		}
		registered = true;
		ItemComponentTooltipProviderRegistry.addLast(BUATAN_MURID, karya -> (context, consumer, flag, components) ->
				consumer.accept(Component.translatable(DataKerja.P + "tanda.buatan", karya.nama().isEmpty()
						? Component.translatable(DataKerja.P + "tanda.tanpa_nama") : Component.literal(karya.nama()))
						.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
		ItemComponentTooltipProviderRegistry.addLast(BELI_MURID, soul -> (context, consumer, flag, components) ->
				consumer.accept(Component.translatable(DataKerja.P + "tanda.beli").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
	}

	/** Marks {@code stack} as the work of {@code maker} and returns it. */
	public static ItemStack tandaiBuatan(ItemStack stack, DiscipleEntity maker) {
		if (stack != null && !stack.isEmpty() && maker != null) {
			stack.set(BUATAN_MURID, new Karya(maker.soulId(), maker.getName().getString()));
		}
		return stack;
	}

	/** Marks a purchase of {@code soul} (39C calls this or sets the component by id). */
	public static ItemStack tandaiBeli(ItemStack stack, UUID soul) {
		if (stack != null && !stack.isEmpty() && soul != null) {
			stack.set(BELI_MURID, soul);
		}
		return stack;
	}

	/** The maker of a disciple's own work, or null. */
	public static @Nullable Karya buatan(ItemStack stack) {
		return stack == null || stack.isEmpty() ? null : stack.get(BUATAN_MURID);
	}

	/** True when a disciple bought this stack. */
	public static boolean dibeli(ItemStack stack) {
		return stack != null && !stack.isEmpty() && stack.has(BELI_MURID);
	}

	private KerjaKomponen() {
	}
}
