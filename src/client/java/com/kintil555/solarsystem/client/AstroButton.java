package com.kintil555.solarsystem.client;

import java.util.function.BooleanSupplier;

import com.daqem.uilib.gui.widget.ButtonWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/** Flat "modern astro" button: deep-space fill, cyan outline and glow when selected. */
final class AstroButton extends ButtonWidget {
	enum Icon { NONE, UP, DOWN }

	static final int ACCENT = 0xFF4FD8FF;
	static final int TEXT = 0xFFE8F0FF;
	static final int TEXT_DIM = 0xFF8FA3C8;

	private final BooleanSupplier selected;
	private final Icon icon;

	AstroButton(int x, int y, int width, int height, Component label, Icon icon, BooleanSupplier selected, Runnable action) {
		super(x, y, width, height, label, button -> action.run());
		this.icon = icon;
		this.selected = selected;
	}

	@Override
	protected void extractContents(@NotNull GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		boolean on = selected.getAsBoolean();
		boolean hover = isHoveredOrFocused();
		int x = getX();
		int y = getY();
		int w = getWidth();
		int h = getHeight();

		g.fillGradient(x, y, x + w, y + h, on ? 0xFF1B4E78 : hover ? 0xFF1E2D52 : 0xFF121B36, on ? 0xFF11304F : hover ? 0xFF16223F : 0xFF0C1329);
		g.outline(x, y, w, h, on ? ACCENT : hover ? 0xFF6C86B8 : 0xFF2A3A62);
		if (on) {
			g.fill(x + 2, y + h - 2, x + w - 2, y + h - 1, ACCENT);
		}

		int color = on ? 0xFFFFFFFF : hover ? TEXT : TEXT_DIM;
		if (icon == Icon.NONE) {
			g.centeredText(Minecraft.getInstance().font, getMessage(), x + w / 2, y + (h - 8) / 2, color);
			return;
		}
		// chevron, drawn from rectangles so it does not depend on a glyph in the font
		int cx = x + w / 2;
		int cy = y + h / 2;
		for (int i = 0; i < 4; i++) {
			int dy = icon == Icon.UP ? i - 2 : 1 - i;
			g.fill(cx - 4 + i, cy + dy, cx - 2 + i, cy + dy + 1, color);
			g.fill(cx + 3 - i, cy + dy, cx + 5 - i, cy + dy + 1, color);
		}
	}
}
