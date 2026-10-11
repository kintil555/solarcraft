package com.kintil555.solarsystem.client;

/**
 * A body made of flat-coloured quads (cells) on a unit shape: either a cube (corners at +-1, flat faces) or a
 * sphere. Positions are scaled/rotated at draw time.
 */
final class BodyMesh {
	/** Face normals + up vectors of the 6 cube faces: {nx,ny,nz, ux,uy,uz}. */
	static final int[][] CUBE_FRAMES = {
			{0, 0, 1, 0, 1, 0}, {-1, 0, 0, 0, 1, 0}, {1, 0, 0, 0, 1, 0},
			{0, 0, -1, 0, 1, 0}, {0, 1, 0, 0, 0, -1}, {0, -1, 0, 0, 0, 1}};

	/** 4 corners * xyz per cell. */
	final float[] corners;
	/** Unit direction of each cell centre (used for procedural painting). */
	final float[] normals;
	/** Cell centre (cube: on the cube surface, sphere: unit sphere). */
	final float[] centers;
	/** Unit normal used for culling and lighting (cube: flat face normal). */
	final float[] shade;
	/** Base ARGB colour of each cell. */
	final int[] colors;
	final int cells;
	final boolean cube;

	private BodyMesh(int cells, boolean cube) {
		this.cells = cells;
		this.cube = cube;
		this.corners = new float[cells * 12];
		this.normals = new float[cells * 3];
		this.centers = new float[cells * 3];
		this.shade = new float[cells * 3];
		this.colors = new int[cells];
	}

	private static float[] norm(float[] p) {
		float len = (float) Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
		return new float[] {p[0] / len, p[1] / len, p[2] / len};
	}

	private void setCell(int cell, float[] p0, float[] p1, float[] p2, float[] p3, float[] faceNormal) {
		float[][] ps = {p0, p1, p2, p3};
		float cx = 0, cy = 0, cz = 0;
		for (int i = 0; i < 4; i++) {
			float[] p = cube ? ps[i] : norm(ps[i]);
			int o = cell * 12 + i * 3;
			corners[o] = p[0];
			corners[o + 1] = p[1];
			corners[o + 2] = p[2];
			cx += p[0] * 0.25f;
			cy += p[1] * 0.25f;
			cz += p[2] * 0.25f;
		}
		float[] dir = norm(new float[] {cx, cy, cz});
		System.arraycopy(dir, 0, normals, cell * 3, 3);
		float[] shadeN = faceNormal != null ? faceNormal : dir;
		System.arraycopy(shadeN, 0, shade, cell * 3, 3);
		if (cube) {
			centers[cell * 3] = cx;
			centers[cell * 3 + 1] = cy;
			centers[cell * 3 + 2] = cz;
		} else {
			System.arraycopy(dir, 0, centers, cell * 3, 3);
		}
	}

	private static float[] latLon(double lon, double lat) {
		double c = Math.cos(lat);
		return new float[] {(float) (c * Math.cos(lon)), (float) Math.sin(lat), (float) (c * Math.sin(lon))};
	}

	/** Longitude/latitude sphere (kept for reference/tests). */
	static BodyMesh latLong(int cols, int rows) {
		BodyMesh m = new BodyMesh(cols * rows, false);
		for (int r = 0; r < rows; r++) {
			double lat0 = Math.PI / 2 - Math.PI * r / rows;
			double lat1 = Math.PI / 2 - Math.PI * (r + 1) / rows;
			for (int c = 0; c < cols; c++) {
				double lon0 = 2 * Math.PI * c / cols;
				double lon1 = 2 * Math.PI * (c + 1) / cols;
				m.setCell(r * cols + c, latLon(lon0, lat0), latLon(lon1, lat0), latLon(lon1, lat1), latLon(lon0, lat1), null);
			}
		}
		return m;
	}

	/** A cube with {@code per x per} flat cells on each face. */
	static BodyMesh cube(int per) {
		return cubeFaces(per, 1, null, 0, true);
	}

	/**
	 * Earth cube: each of the 6 faces is {@code pixels x pixels} source pixels from {@code atlas}
	 * (faces laid out 3 columns x 2 rows, face f at col f%3, row f/3).
	 */
	static BodyMesh earthCube(int pixels, int[] atlas, int atlasWidth) {
		return cubeFaces(pixels, pixels, atlas, atlasWidth, true);
	}

	private static BodyMesh cubeFaces(int perFace, int pixels, int[] atlas, int atlasWidth, boolean cube) {
		BodyMesh m = new BodyMesh(6 * perFace * perFace, cube);
		int cell = 0;
		for (int f = 0; f < 6; f++) {
			int[] fr = CUBE_FRAMES[f];
			float[] n = {fr[0], fr[1], fr[2]};
			float[] u = {fr[3], fr[4], fr[5]};
			// right = up x normal
			float[] r = {u[1] * n[2] - u[2] * n[1], u[2] * n[0] - u[0] * n[2], u[0] * n[1] - u[1] * n[0]};
			int col = f % 3, row = f / 3;
			for (int j = 0; j < perFace; j++) {
				for (int i = 0; i < perFace; i++) {
					float s0 = (float) i / perFace, s1 = (float) (i + 1) / perFace;
					float t0 = (float) j / perFace, t1 = (float) (j + 1) / perFace;
					m.setCell(cell, cubePoint(n, r, u, s0, t0), cubePoint(n, r, u, s1, t0),
							cubePoint(n, r, u, s1, t1), cubePoint(n, r, u, s0, t1), n);
					if (atlas != null) {
						int px = col * pixels + i * pixels / perFace;
						int py = row * pixels + j * pixels / perFace;
						m.colors[cell] = atlas[py * atlasWidth + px] | 0xFF000000;
					}
					cell++;
				}
			}
		}
		return m;
	}

	private static float[] cubePoint(float[] n, float[] r, float[] u, float s, float t) {
		float a = 2 * s - 1;
		float b = 1 - 2 * t;
		return new float[] {n[0] + a * r[0] + b * u[0], n[1] + a * r[1] + b * u[1], n[2] + a * r[2] + b * u[2]};
	}
}
