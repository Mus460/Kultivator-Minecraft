package dev.kultivator.client.dunia.dimensi.render;

import dev.kultivator.Kultivator;
import dev.kultivator.client.disciple.DiscipleRenderState;
import dev.kultivator.client.dunia.dimensi.DimensiState;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.dunia.dimensi.DimensiNpc;
import dev.kultivator.dunia.dimensi.bawah.ArwahEntity;
import dev.kultivator.sosok.GerakId;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * The people of Dunia Bawah ({@link ArwahEntity}): Meng Po in faded hemp with her white bun and pins, stirring her
 * cauldron with a ladle while nobody speaks to her; the Ten Kings in the court's colour under the winged black hat, brush
 * and ledger in hand, now and then raising the brush; King Yama in black and crimson under the crown board with jade
 * tassels, a head taller; Ox-Head and Horse-Face in dark leather on D's own skins with their beast heads, the fork
 * shouldered; Bai Wuchang in white with the tall white hat and the soul lantern, Hei Wuchang in black with the binding
 * chain; ghosts in white mourning, see-through and pale, hovering; hungry ghosts grey, see-through, hunched, reaching,
 * with long black hair and burning eyes that burn redder under the blood eclipse.
 * <p>
 * DESIGN 40.4.1 D (module 40R): Meng Po, the judges, King Yama and the dead are Sosok bodies ({@code d.meng_po},
 * {@code d.hakim}, {@code d.raja_yama}, {@code d.arwah}: an old woman, men of the court, the dead of every age in burial
 * white with the left-over-right collar); Ox-Head, Horse-Face, the two Impermanences and the hungry ghosts keep their
 * own iconic skins and are only animated by Gerak. Their work is a clip: Meng Po stirs the soup ({@code masak}), a judge
 * writes in his ledger and now and then strikes the gavel ({@code palu_hakim}), the guards hold the fork
 * ({@code sikap_tombak}), Bai Wuchang raises his lantern ({@code main_lentera}), Hei Wuchang folds his arms over the chain.
 * The dead float by Gerak's {@code arwah} walk instead of the old hover.
 */
public class ArwahRenderer extends HumanNpcRenderer<ArwahEntity> {

	private static final Identifier OX_SKIN = Kultivator.id("textures/dunia/dimensi/entity/kepala_lembu.png");
	private static final Identifier HORSE_SKIN = Kultivator.id("textures/dunia/dimensi/entity/muka_kuda.png");
	private static final Identifier GHOST_SKIN = Kultivator.id("textures/dunia/dimensi/entity/hantu_lapar.png");
	private static final Identifier BAI_SKIN = Kultivator.id("textures/dunia/dimensi/entity/wuchang_putih.png");
	private static final Identifier HEI_SKIN = Kultivator.id("textures/dunia/dimensi/entity/wuchang_hitam.png");
	/** The robes of the Ten Courts (Qinguang ... Zhuanlun). */
	private static final int[] COURT_ROBE = {0x6A1A1A, 0x1E2A5A, 0x2A3A2A, 0x5A1A3A, 0x3A1A0A, 0x2A2A3A, 0x4A2A0A, 0x1A3A4A, 0x3A2A4A, 0x5A4A1A};
	/** DESIGN 40.4.1 D look keys by role. */
	private static final String[] RUPA = new String[ArwahEntity.ROLES];

	static {
		RUPA[ArwahEntity.MENG_PO] = "d.meng_po";
		RUPA[ArwahEntity.HAKIM] = "d.hakim";
		RUPA[ArwahEntity.KEPALA_LEMBU] = "d.kepala_lembu";
		RUPA[ArwahEntity.MUKA_KUDA] = "d.muka_kuda";
		RUPA[ArwahEntity.ARWAH] = "d.arwah";
		RUPA[ArwahEntity.HANTU_LAPAR] = "d.hantu_lapar";
		RUPA[ArwahEntity.RAJA_YAMA] = "d.raja_yama";
		RUPA[ArwahEntity.WUCHANG_PUTIH] = "d.wuchang_putih";
		RUPA[ArwahEntity.WUCHANG_HITAM] = "d.wuchang_hitam";
	}

	public ArwahRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected void look(ArwahEntity entity, DiscipleRenderState state) {
		int role = entity.role();
		int nascent = Realm.NASCENT_SOUL.ordinal();
		switch (role) {
			case ArwahEntity.MENG_PO -> {
				skin(state, 3, true);
				set(state, 0xB8AE98, nascent, Headwear.MENG_PO, 0.9F, ARGB.color(255, 236, 228, 220));
			}
			case ArwahEntity.HAKIM -> {
				skin(state, entity.look(), false);
				set(state, COURT_ROBE[Mth.clamp(entity.variant(), 1, 10) - 1], Realm.DEITY_TRANSFORMATION.ordinal(), Headwear.JUDGE, 1.04F, -1);
			}
			case ArwahEntity.RAJA_YAMA -> {
				skin(state, 1, false);
				set(state, 0x2A0A10, Realm.TRUE_IMMORTAL.ordinal(), Headwear.YAMA, 1.14F, ARGB.color(255, 246, 226, 222));
			}
			case ArwahEntity.KEPALA_LEMBU -> {
				ownSkin(state, OX_SKIN);
				set(state, 0x3A2A1E, Realm.GOLDEN_CORE.ordinal(), Headwear.OX, 1.1F, -1);
			}
			case ArwahEntity.MUKA_KUDA -> {
				ownSkin(state, HORSE_SKIN);
				set(state, 0x22242E, Realm.GOLDEN_CORE.ordinal(), Headwear.HORSE, 1.1F, -1);
			}
			case ArwahEntity.WUCHANG_PUTIH -> {
				ownSkin(state, BAI_SKIN);
				// the white Impermanence: deathly pale, robed in mourning white
				set(state, 0xEDEBE6, Realm.NASCENT_SOUL.ordinal(), Headwear.WUCHANG_PUTIH, 1.12F, ARGB.color(255, 238, 236, 244));
			}
			case ArwahEntity.WUCHANG_HITAM -> {
				ownSkin(state, HEI_SKIN);
				set(state, 0x16151A, Realm.NASCENT_SOUL.ordinal(), Headwear.WUCHANG_HITAM, 1.12F, ARGB.color(255, 196, 192, 204));
			}
			case ArwahEntity.HANTU_LAPAR -> {
				ownSkin(state, GHOST_SKIN);
				state.role = DiscipleEntity.Role.PHANTOM.ordinal();
				float flicker = 0.85F + 0.15F * Mth.sin(state.ageInTicks * 0.3F + entity.getId());
				set(state, 0x3A3444, Realm.QI_REFINING.ordinal(), Headwear.GHOST_HAIR, 1.0F,
						ARGB.color(Math.round(170 * flicker), 150, 140, 170));
				((FabricRenderState) state).setData(HeadwearLayer.GLOW, Headwear.GHOST_EYES);
				hover(state, 0.08F, entity.getId());
			}
			default -> {
				skin(state, entity.look(), (entity.look() & 1) == 1);
				state.role = DiscipleEntity.Role.PHANTOM.ordinal();
				set(state, 0xE8E8F0, Realm.QI_REFINING.ordinal(), Headwear.NONE, 1.0F, ARGB.color(140, 200, 220, 255));
				// the float itself is Gerak's arwah walk (LIFT 0.25, 40.5.6); a small hover keeps the feet off the floor
				hover(state, 0.04F, entity.getId());
			}
		}
	}

	@Override
	protected String rupa(ArwahEntity entity) {
		return RUPA[Mth.clamp(entity.role(), 0, ArwahEntity.ROLES - 1)];
	}

	/** Ox-Head, Horse-Face, the Impermanences and the hungry ghosts are iconic own skins (40.4.1 D). */
	@Override
	protected boolean kulitSendiri(ArwahEntity entity) {
		int role = entity.role();
		return role == ArwahEntity.KEPALA_LEMBU || role == ArwahEntity.MUKA_KUDA || role == ArwahEntity.WUCHANG_PUTIH
				|| role == ArwahEntity.WUCHANG_HITAM || role == ArwahEntity.HANTU_LAPAR;
	}

	@Override
	protected int lookSosok(ArwahEntity entity) {
		return entity.look() * 31 + entity.variant();
	}

	/** The dead used slim arms for women ({@code look & 1}); the court's sexes are fixed by policy. */
	@Override
	protected int slimLama(ArwahEntity entity) {
		return entity.role() == ArwahEntity.ARWAH ? entity.look() & 1 : -1;
	}

	@Override
	protected void pose(ArwahEntity entity, DiscipleRenderState state, boolean talking, boolean still) {
		if (sosokGerak(state) && klipPeran(entity, state, talking, still)) {
			return;
		}
		float age = state.ageInTicks;
		switch (entity.role()) {
			// Meng Po stirs the soup (the kitchen duty pose with its ladle) whenever she is not speaking
			case ArwahEntity.MENG_PO -> {
				if (!talking && still) {
					state.activity = DiscipleEntity.ACTIVITY_KITCHEN;
				}
			}
			// the judge raises his brush now and then, as if writing in the air above the ledger
			case ArwahEntity.HAKIM -> {
				if (!talking && still && Mth.sin(age * 0.05F + entity.getId()) > 0.6F) {
					state.rightArmPose = HumanoidModel.ArmPose.BLOCK;
					state.xRot = Math.max(state.xRot, 18.0F);
				}
			}
			// King Yama, hand to his beard in thought
			case ArwahEntity.RAJA_YAMA -> {
				if (!talking && still) {
					state.rightArmPose = HumanoidModel.ArmPose.TOOT_HORN;
				}
			}
			// the guards stand ready, the fork held forward
			case ArwahEntity.KEPALA_LEMBU, ArwahEntity.MUKA_KUDA -> {
				if (still && !entity.hasFlag(DimensiNpc.FLAG_FIGHT)) {
					state.rightArmPose = HumanoidModel.ArmPose.BLOCK;
				}
			}
			// Bai Wuchang holds his lantern out to light the way
			case ArwahEntity.WUCHANG_PUTIH -> {
				if (still) {
					state.rightArmPose = HumanoidModel.ArmPose.BLOCK;
				}
			}
			// the hungry dead hunch forward with their hands reaching
			case ArwahEntity.HANTU_LAPAR -> {
				state.rightArmPose = HumanoidModel.ArmPose.CROSSBOW_HOLD;
				state.leftArmPose = HumanoidModel.ArmPose.CROSSBOW_HOLD;
				state.xRot = Math.max(state.xRot, 12.0F);
				if (DimensiState.sky() == 2) {
					// the blood eclipse: their eyes burn redder and they tremble
					((FabricRenderState) state).setData(TINT, ARGB.color(180, 190, 120, 140));
				}
			}
			// the dead sway as they drift; an escorted soul keeps its head bowed
			case ArwahEntity.ARWAH -> {
				state.xRot = Math.max(state.xRot, entity.hasFlag(DimensiNpc.FLAG_FOLLOW) ? 25.0F : 8.0F);
				state.bodyRot += Mth.sin(age * 0.07F + entity.getId()) * 4.0F;
			}
			default -> {
			}
		}
	}

	/** The roles' work as Gerak clips; false lets the old pose run (the hungry ghosts and the dead keep theirs). */
	private static boolean klipPeran(ArwahEntity entity, DiscipleRenderState state, boolean talking, boolean still) {
		float age = state.ageInTicks;
		switch (entity.role()) {
			case ArwahEntity.MENG_PO -> {
				if (!talking && still) {
					klip(state, GerakId.MASAK);
				}
				return true;
			}
			case ArwahEntity.HAKIM -> {
				if (!talking && still) {
					klip(state, Mth.sin(age * 0.05F + entity.getId()) > 0.85F ? GerakId.PALU_HAKIM : GerakId.MENULIS);
				}
				return true;
			}
			case ArwahEntity.KEPALA_LEMBU, ArwahEntity.MUKA_KUDA -> {
				if (still && !entity.hasFlag(DimensiNpc.FLAG_FIGHT) && !talking) {
					klip(state, GerakId.SIKAP_TOMBAK);
				}
				return true;
			}
			case ArwahEntity.WUCHANG_PUTIH -> {
				if (still && !talking) {
					klip(state, GerakId.MAIN_LENTERA);
				}
				return true;
			}
			case ArwahEntity.WUCHANG_HITAM -> {
				if (still && !talking) {
					klip(state, GerakId.SIKAP_LIPAT_TANGAN);
				}
				return true;
			}
			case ArwahEntity.RAJA_YAMA -> {
				// his idle stance (angkuh) comes from his look row; nothing to ask
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	@Override
	protected @Nullable Component title(ArwahEntity entity) {
		return switch (entity.role()) {
			case ArwahEntity.HAKIM -> Component.translatable("kultivator.dimensi.gelar.hakim", Mth.clamp(entity.variant(), 1, 10));
			case ArwahEntity.HANTU_LAPAR -> null;
			case ArwahEntity.ARWAH -> Component.translatable(entity.hasFlag(DimensiNpc.FLAG_FOLLOW) ? "kultivator.dimensi.gelar.arwah_ikut"
					: "kultivator.dimensi.gelar.arwah");
			default -> Component.translatable("kultivator.dimensi.gelar." + ArwahEntity.roleId(entity.role()));
		};
	}
}
