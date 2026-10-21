package dev.kultivator.client.epic.kosmik;

/**
 * A stage anchor frame (contract C32.2): an origin in stage metres, a forward and an up unit vector and a scale. Points
 * given in it ({@code FilmSpace.V} keys) are {@code origin + (right·x + up·y + forward·z) × scale} with
 * {@code right = forward × up}. Mutable and reused (no per-frame allocation).
 */
public final class StageAnchor {

	public double x;
	public double y;
	public double z;
	public double fx;
	public double fy;
	public double fz = 1.0D;
	public double ux;
	public double uy = 1.0D;
	public double uz;
	public double scale = 1.0D;

	/** Origin, forward +z, up +y, scale 1. */
	public StageAnchor reset() {
		return set(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 1.0D, 0.0D, 1.0D, 0.0D, 1.0D);
	}

	/** Sets every field; the axes are re-orthonormalised (up is made perpendicular to forward). */
	public StageAnchor set(double ox, double oy, double oz, double fwdX, double fwdY, double fwdZ, double upX, double upY, double upZ, double s) {
		this.x = ox;
		this.y = oy;
		this.z = oz;
		double fl = Math.sqrt(fwdX * fwdX + fwdY * fwdY + fwdZ * fwdZ);
		if (!(fl > 1.0E-9D)) {
			fwdX = 0.0D;
			fwdY = 0.0D;
			fwdZ = 1.0D;
			fl = 1.0D;
		}
		this.fx = fwdX / fl;
		this.fy = fwdY / fl;
		this.fz = fwdZ / fl;
		double dot = upX * fx + upY * fy + upZ * fz;
		double px = upX - fx * dot;
		double py = upY - fy * dot;
		double pz = upZ - fz * dot;
		double ul = Math.sqrt(px * px + py * py + pz * pz);
		if (!(ul > 1.0E-9D)) {
			// forward is (anti)parallel to the given up: take any perpendicular
			if (Math.abs(fy) < 0.9D) {
				px = -fx * fy;
				py = 1.0D - fy * fy;
				pz = -fz * fy;
			} else {
				px = 1.0D - fx * fx;
				py = -fy * fx;
				pz = -fz * fx;
			}
			ul = Math.sqrt(px * px + py * py + pz * pz);
		}
		this.ux = px / ul;
		this.uy = py / ul;
		this.uz = pz / ul;
		this.scale = Double.isFinite(s) && s > 0.0D ? s : 1.0D;
		return this;
	}

	/** Origin and scale only; forward +z, up +y. */
	public StageAnchor at(double ox, double oy, double oz, double s) {
		return set(ox, oy, oz, 0.0D, 0.0D, 1.0D, 0.0D, 1.0D, 0.0D, s);
	}

	public double rx() {
		return fy * uz - fz * uy;
	}

	public double ry() {
		return fz * ux - fx * uz;
	}

	public double rz() {
		return fx * uy - fy * ux;
	}

	/** The stage point of local ({@code lx}, {@code ly}, {@code lz}) (x right, y up, z forward, × scale) into {@code out[0..2]}. */
	public void place(double lx, double ly, double lz, double[] out) {
		double rx = rx();
		double ry = ry();
		double rz = rz();
		out[0] = x + (rx * lx + ux * ly + fx * lz) * scale;
		out[1] = y + (ry * lx + uy * ly + fy * lz) * scale;
		out[2] = z + (rz * lx + uz * ly + fz * lz) * scale;
	}

	public void copy(StageAnchor other) {
		this.x = other.x;
		this.y = other.y;
		this.z = other.z;
		this.fx = other.fx;
		this.fy = other.fy;
		this.fz = other.fz;
		this.ux = other.ux;
		this.uy = other.uy;
		this.uz = other.uz;
		this.scale = other.scale;
	}
}
