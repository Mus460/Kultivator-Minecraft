package dev.kultivator.sect.build.api;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The Gudang Bahan Sekte as the construction engine sees it (DESIGN 21.13, 21.24): implemented by the flow module
 * ({@code sect.build.flow.Ledger}), registered with {@link #register}, called by the engine on the server thread.
 * Without a registered ledger {@link #get()} returns a safe default that reserves nothing (so nothing is built) and keeps
 * nothing.
 * <p>
 * Source: <i>A Record of a Mortal's Journey</i> ch. 153, Yue Lu Hall stores and supplies materials (the Gudang). The
 * categories, numbers and costs are game design.
 */
public interface MaterialLedger {

	/** Takes the whole bill from the owner's ledger, all-or-nothing; false (nothing taken) when it is not covered. */
	boolean reserve(UUID owner, Bill bill);

	/** Gives back the unused part of a reservation (positions found already correct, a skipped or cancelled component). */
	void release(UUID owner, Bill unused);

	/**
	 * Credits {@code centi} centi-units of {@code cat}: digging credit, break refund, explosion credit, dismantling.
	 * {@code reason} is a short id for the log ("dig", "break", "explosion", "dismantle", ...).
	 */
	void credit(UUID owner, Bahan cat, long centi, String reason);

	/** Puts an item into the Peti Galian (salvage counter, no limit): ores, gems, leftovers. Never lost. */
	void salvage(UUID owner, ItemStack stack);

	/** What one {@code item} is worth when deposited, in centi-units of its category (0 when not accepted). */
	long depositValueCenti(Item item);

	/** The category {@code item} is deposited into, or null when it is not accepted (or is an exact item). */
	@Nullable Bahan categoryOf(Item item);

	// ================================================================== registration

	/** The registered ledger, or the safe default. */
	static MaterialLedger get() {
		return Holder.ledger;
	}

	/** Called once by the flow module's {@code init()}; null restores the default. */
	static void register(@Nullable MaterialLedger ledger) {
		Holder.ledger = ledger == null ? Holder.NONE : ledger;
	}

	/** True once the flow module registered its ledger. */
	static boolean registered() {
		return Holder.ledger != Holder.NONE;
	}

	/** Keeps the registered ledger (not part of the API). */
	final class Holder {
		private static final MaterialLedger NONE = new MaterialLedger() {
			@Override
			public boolean reserve(UUID owner, Bill bill) {
				return bill == null || bill.isEmpty();
			}

			@Override
			public void release(UUID owner, Bill unused) {
			}

			@Override
			public void credit(UUID owner, Bahan cat, long centi, String reason) {
			}

			@Override
			public void salvage(UUID owner, ItemStack stack) {
			}

			@Override
			public long depositValueCenti(Item item) {
				return 0L;
			}

			@Override
			public @Nullable Bahan categoryOf(Item item) {
				return null;
			}
		};
		private static volatile MaterialLedger ledger = NONE;

		private Holder() {
		}
	}
}
