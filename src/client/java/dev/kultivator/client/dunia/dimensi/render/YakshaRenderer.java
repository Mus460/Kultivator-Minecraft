package dev.kultivator.client.dunia.dimensi.render;

import dev.kultivator.Kultivator;
import dev.kultivator.client.disciple.DiscipleRenderState;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.dimensi.laut.YakshaEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/**
 * The yaksha ({@link YakshaEntity}): D's blue-skinned demon skin with a loincloth and arm rings, horns, tusks and flame
 * hair, the trident in hand. Sea yaksha are a deeper teal; the rebel chief stands half again as tall with a red cast;
 * the courts' patrols wear a dark sash (the robe layer at Qi Refining).
 */
public class YakshaRenderer extends HumanNpcRenderer<YakshaEntity> {

	private static final Identifier SKIN = Kultivator.id("textures/dunia/dimensi/entity/yaksha.png");

	public YakshaRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected void look(YakshaEntity entity, DiscipleRenderState state) {
		ownSkin(state, SKIN);
		boolean sea = entity.variant() == YakshaEntity.LAUT;
		float scale = entity.isChief() ? 1.45F : 1.12F + 0.03F * entity.tier();
		int tint = entity.isChief() ? ARGB.color(255, 255, 200, 190) : sea ? ARGB.color(255, 200, 240, 236) : -1;
		int realm = sea ? Realm.MORTAL.ordinal() : Realm.QI_REFINING.ordinal();
		set(state, 0x1A1420, realm, Headwear.YAKSHA, scale, tint);
	}
}
