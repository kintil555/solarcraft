package com.kintil555.solarsystem.client;

import com.daqem.uilib.gui.component.AbstractComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Background, status line and descriptive text of the toolbar; the buttons are separate uilib widgets. */
final class ToolbarPanel extends AbstractComponent {
	private static final int BG_TOP = 0xF2080C1C;
	private static final int BG_BOTTOM = 0xF20F1A36;
	private static final int BORDER = 0x553A5A9A;
	private static final int GREEN = 0xFF5CE6A0;

	ToolbarPanel(int x, int y, int width, int height) {
		super(x, y, width, height);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, int parentWidth, int parentHeight) {
		Font font = Minecraft.getInstance().font;
		int x = getTotalX();
		int y = getTotalY();
		int w = getWidth();
		int h = getHeight();

		g.fillGradient(x, y, x + w, y + h, BG_TOP, BG_BOTTOM);
		g.outline(x, y, w, h, BORDER);
		g.fill(x + 1, y, x + w - 1, y + 1, 0xAA4FD8FF);
		g.fill(x, y, x + 8, y + 2, AstroButton.ACCENT);
		g.fill(x + w - 8, y, x + w, y + 2, AstroButton.ACCENT);

		// status line (left of the chevron button)
		boolean free = SolarView.mode == SolarView.Mode.FREE;
		g.fill(x + 10, y + 11, x + 14, y + 15, free ? AstroButton.ACCENT : GREEN);
		StringBuilder status = new StringBuilder(free ? "FREE" : "EXPLORE").append("  |  ").append(SolarView.style.label);
		if (SolarView.tool != SolarView.Tool.NONE) {
			status.append("  |  ").append(ToolbarScreen.toolLabel(SolarView.tool));
		}
		g.text(font, status.toString(), x + 20, y + 9, AstroButton.TEXT);
		String hint = SolarSystemClient.cameraKey().getTranslatedKeyMessage().getString() + ": mode kamera";
		g.text(font, hint, x + w - 10 - font.width(hint), y + 9, AstroButton.TEXT_DIM);

		if (!SolarView.expanded) {
			return;
		}
		g.fill(x + 8, y + ToolbarScreen.BAR_H, x + w - 8, y + ToolbarScreen.BAR_H + 1, 0x553A5A9A);

		int tx = x + 10;
		int ty = y + ToolbarScreen.DESC_Y;
		int tw = w - 20;
		if (SolarView.tab == SolarView.Tab.KEYS) {
			drawKeys(g, font, tx, ty, tw);
		} else {
			g.textWithWordWrap(font, Component.literal(describe()), tx, ty, tw, AstroButton.TEXT_DIM);
		}
	}

	private static String describe() {
		return switch (SolarView.tab) {
			case CAMERA -> SolarView.mode == SolarView.Mode.FREE
					? "Free: scroll zoom, klik kanan geser, klik kiri orbit. Klik planet (hijau) lalu tarik dengan klik kiri untuk melempar orbitnya."
					: "Explore: terbang seperti biasa. Pilih Free untuk kamera orbit dengan kursor.";
			case TOOLS -> switch (SolarView.tool) {
				case NONE -> "Pilih sebuah tool, lalu klik planet atau ruang kosong di dunia.";
				case LASER -> "Laser: tahan klik kiri pada planet. Planet batu hangus, planet gas terkikis ke dalam.";
				case RING -> "Planet Ring: klik planet untuk memasang atau melepas cincin partikel.";
				case ASTEROID -> "Asteroid: klik di bidang orbit untuk melepas asteroid yang mengorbit Matahari.";
				case MOON -> "Moon: klik planet untuk menambah bulan (maksimal 4 per planet).";
			};
			case STYLE -> SolarView.style.description;
			case KEYS -> "";
		};
	}

	private static void drawKeys(GuiGraphicsExtractor g, Font font, int x, int y, int width) {
		String cam = SolarSystemClient.cameraKey().getTranslatedKeyMessage().getString();
		String bar = SolarSystemClient.toolbarKey().getTranslatedKeyMessage().getString();
		String[] left = {
				"Mode Free",
				"Scroll : zoom",
				"Tahan klik kanan : geser",
				"Tahan klik kiri : orbit",
				"Klik planet, lalu tarik : luncurkan"};
		String[] right = {
				"Umum",
				cam + " : ganti Free / Explore",
				bar + " : buka / tutup toolbar",
				"1-4 : pilih tool, ESC : keluar",
				"Laser : tahan klik kiri"};
		int half = width / 2;
		for (int i = 0; i < left.length; i++) {
			int color = i == 0 ? AstroButton.ACCENT : AstroButton.TEXT_DIM;
			g.text(font, left[i], x, y + i * 11, color);
			g.text(font, right[i], x + half, y + i * 11, color);
		}
	}
}
