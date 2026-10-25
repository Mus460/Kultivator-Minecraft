package dev.kultivator.client.screen.ui;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The single turning preview of the kit (DESIGN 43.7, moved from {@code tempat/ui/kit}; 38.U.2): a turning preview in a 64-96 px box on a lacquer pedestal, 20°/s, drag to turn
 * it by hand (it resumes turning 2 s after the drag).
 * <ul>
 * <li><b>Entity</b> (beast cubs, ships, the rotating stone): the real model as a picture-in-picture ({@code entity()}),
 * yawed by the turn angle and tilted 25° towards the viewer. A preview entity is created on the client once per type id
 * and never added to the level.</li>
 * <li><b>Item</b>: in 26.3 the GUI item renderer is 2D (the pose is a 3 x 2 matrix), so the item is drawn 4x and "turned"
 * by scaling its width with cos(angle), with a light sweep and a ground shadow that follow the turn.</li>
 * <li><b>Fallback</b> below 32 px (the narrow layout, 38.U.2): the 32 px icon only.</li>
 * </ul>
 */
public final class Pratinjau3D {

	private static final float DERAJAT_PER_DETIK = 20.0F;
	private static final float MIRING = (float) Math.toRadians(25.0D);
	private static final long JEDA_SERET_MS = 2000L;

	private final Map<String, Entity> entitas = new HashMap<>();
	private float sudut = 30.0F;
	private long seretTerakhir;
	private boolean seret;
	private int x0;
	private int y0;
	private int x1;
	private int y1;

	/** Draws the preview of an item (or an entity type id when {@code entitasId} is set). */
	public void gambar(Lukis l, String kunci, int x0, int y0, int x1, int y1, ItemStack item, @Nullable String entitasId, int ikon) {
		this.x0 = x0;
		this.y0 = y0;
		this.x1 = x1;
		this.y1 = y1;
		l.semuaPratinjau.add(this);
		if (!seret && l.kini - seretTerakhir > JEDA_SERET_MS) {
			sudut = (sudut + DERAJAT_PER_DETIK * l.detik) % 360.0F;
		}
		int w = x1 - x0;
		int h = y1 - y0;
		int size = Math.min(w, h);
		float cx = (x0 + x1) / 2.0F;
		float cy = (y0 + y1) / 2.0F;
		// pedestal: a shadowed lacquer disc
		Brush.disc(l.g, cx, y1 - size * 0.12F, size * 0.38F, Palette.argb(0x40, 0x000000));
		l.g.fill(Math.round(cx - size * 0.36F), Math.round(y1 - size * 0.14F), Math.round(cx + size * 0.36F), Math.round(y1 - size * 0.08F),
				Palette.argb(0x90, 0x5A1810));
		l.g.fill(Math.round(cx - size * 0.36F), Math.round(y1 - size * 0.14F), Math.round(cx + size * 0.36F), Math.round(y1 - size * 0.13F),
				Palette.argb(0xB0, Palette.GOLD & 0xFFFFFF));
		l.zona(kunci, x0, y0, x1, y1, null, true, null, false);
		if (size < 32) {
			if (!item.isEmpty()) {
				l.g.item(item, Math.round(cx - 8), Math.round(cy - 8));
			} else {
				Glyphs.Atlas.ikon(l.g, ikon, Math.round(cx - 8), Math.round(cy - 8), 16, 0xFFFFFFFF);
			}
			return;
		}
		Entity e = entitasId == null || entitasId.isEmpty() ? null : entitas(entitasId);
		if (e != null && gambarEntitas(l, e, x0, y0, x1, y1)) {
			return;
		}
		if (!item.isEmpty()) {
			float rad = (float) Math.toRadians(sudut);
			float cos = (float) Math.cos(rad);
			float lebar = Math.max(0.08F, Math.abs(cos));
			float skala = size * 0.62F / 16.0F;
			// ground shadow follows the turn
			Brush.disc(l.g, cx, cy + size * 0.30F, size * 0.22F * (0.6F + 0.4F * lebar), Palette.argb(0x30, 0x000000));
			l.g.pose().pushMatrix();
			l.g.pose().translate(cx, cy - size * 0.04F + (float) Math.sin(l.kini / 600.0D) * 1.5F);
			l.g.pose().scale(skala * lebar * Math.signum(cos == 0 ? 1 : cos), skala);
			l.g.pose().translate(-8.0F, -8.0F);
			l.g.item(item, 0, 0);
			l.g.pose().popMatrix();
			// a light sweep across the face when it turns towards the viewer
			float sweep = (float) Math.sin(rad * 2.0D);
			if (lebar > 0.5F) {
				float sx = cx + sweep * size * 0.25F;
				Brush.line(l.g, sx - 3, cy - size * 0.25F, sx + 3, cy + size * 0.2F, 2.0F, Palette.argb(Math.round(40 * lebar), 0xFFFFFF));
			}
			return;
		}
		Glyphs.Atlas.ikon(l.g, ikon, Math.round(cx - 16), Math.round(cy - 16), 32, 0xFFFFFFFF);
	}

	private boolean gambarEntitas(Lukis l, Entity e, int x0, int y0, int x1, int y1) {
		Minecraft minecraft = Minecraft.getInstance();
		EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
		EntityRenderState state;
		try {
			EntityRenderer<? super Entity, ?> renderer = dispatcher.getRenderer(e);
			state = renderer.createRenderState(e, 1.0F);
		} catch (RuntimeException ex) {
			return false;
		}
		state.shadowPieces.clear();
		state.outlineColor = 0;
		state.nameTag = null;
		state.ageInTicks = l.kini / 50.0F;
		if (state instanceof LivingEntityRenderState living) {
			living.bodyRot = 180.0F;
			living.yRot = 0.0F;
			living.xRot = 0.0F;
			living.boundingBoxWidth = living.boundingBoxWidth / living.scale;
			living.boundingBoxHeight = living.boundingBoxHeight / living.scale;
			living.scale = 1.0F;
		}
		float extent = Math.max(0.4F, Math.max(state.boundingBoxHeight, state.boundingBoxWidth * 1.1F));
		int size = Math.min(x1 - x0, y1 - y0);
		float scale = size * 0.78F / extent * l.skala;
		Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI).rotateX(-MIRING).rotateY((float) Math.toRadians(sudut));
		Vector3f translation = new Vector3f(0.0F, state.boundingBoxHeight / 2.0F, 0.0F);
		l.g.entity(state, scale, translation, rotation, null, l.nyata(x0), l.nyata(y0), l.nyata(x1), l.nyata(y1));
		return true;
	}

	private @Nullable Entity entitas(String id) {
		if (entitas.containsKey(id)) {
			return entitas.get(id);
		}
		Minecraft minecraft = Minecraft.getInstance();
		Entity made = null;
		Identifier key = Identifier.tryParse(id);
		if (minecraft.level != null && key != null) {
			EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(key).orElse(null);
			if (type != null) {
				try {
					made = type.create(minecraft.level, EntitySpawnReason.LOAD);
				} catch (RuntimeException ex) {
					made = null;
				}
			}
		}
		entitas.put(id, made);
		return made;
	}

	public boolean berisi(double x, double y) {
		return x >= x0 && y >= y0 && x < x1 && y < y1;
	}

	public void mulaiSeret() {
		seret = true;
	}

	public void seret(double dx, long kini) {
		sudut = (sudut + (float) dx * 2.0F) % 360.0F;
		seretTerakhir = kini;
	}

	public void lepas(long kini) {
		seret = false;
		seretTerakhir = kini;
	}

	public boolean menyeret() {
		return seret;
	}
}
