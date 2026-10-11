package com.kintil555.solarsystem.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import com.daqem.uilib.gui.AbstractScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import com.kintil555.solarsystem.system.SolarSystem;

/**
 * The bottom toolbar. It is a screen so the cursor is free; in Free mode it stays open and forwards
 * scroll/drag on the empty area to {@link FreeCamera}.
 */
final class ToolbarScreen extends AbstractScreen {
	static final int BAR_H = 26;
	static final int DESC_Y = BAR_H + 28;
	private static final int TAB_Y = BAR_H + 5;
	private static final int BTN_Y = DESC_Y + 22;
	private static final int PANEL_H = 102;
	private static final int MAX_WIDTH = 540;
	private static final int MARGIN = 6;
	private static final int PAD = 10;
	private static final int GAP = 4;
	private static final int DRAG_SLOP = 3;

	private record Spec(String label, BooleanSupplier selected, Runnable action) {}

	private int px;
	private int py;
	private int pw;
	private int ph;
	private int dragButton = -1;
	private double pressX;
	private double pressY;
	private boolean moved;
	private boolean aimMode;

	ToolbarScreen() {
		super(Component.literal("Solar Toolbar"));
	}

	static String toolLabel(SolarView.Tool tool) {
		return switch (tool) {
			case NONE -> "Tidak ada";
			case LASER -> "Laser";
			case RING -> "Planet Ring";
			case ASTEROID -> "Asteroid";
			case MOON -> "Moon";
		};
	}

	private static String tabLabel(SolarView.Tab tab) {
		return switch (tab) {
			case CAMERA -> "Camera Mode";
			case TOOLS -> "Tools";
			case KEYS -> "Keybind";
			case STYLE -> "Solar Style";
		};
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void removed() {
		SolarView.setMode(SolarView.Mode.EXPLORE);
		super.removed();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		// keep the world visible: no blur, no dimming
	}

	@Override
	protected void init() {
		if (SolarView.mode == SolarView.Mode.FREE && !SolarView.expanded) {
			// Free mode: only a small arrow at the bottom; the panel opens when it is clicked
			pw = 44;
			ph = 18;
			px = (width - pw) / 2;
			py = height - ph - MARGIN;
			addWidget(new AstroButton(px, py, pw, ph, Component.empty(), AstroButton.Icon.UP, () -> false, () -> {
				SolarView.expanded = true;
				rebuildWidgets();
			}));
			super.init();
			return;
		}
		pw = Math.min(MAX_WIDTH, width - 2 * MARGIN);
		px = (width - pw) / 2;
		ph = SolarView.expanded ? BAR_H + PANEL_H : BAR_H;
		py = height - ph - MARGIN;

		addComponent(new ToolbarPanel(px, py, pw, ph));

		boolean open = SolarView.expanded;
		addWidget(new AstroButton(px + pw / 2 - 22, py + 4, 44, 18, Component.empty(),
				open ? AstroButton.Icon.DOWN : AstroButton.Icon.UP, () -> false, () -> {
					SolarView.expanded = !SolarView.expanded;
					rebuildWidgets();
				}));
		if (open) {
			buildTabs();
			buildContent();
		}
		super.init();
	}

	private void buildTabs() {
		SolarView.Tab[] tabs = SolarView.Tab.values();
		int tw = (pw - 2 * PAD - GAP * (tabs.length - 1)) / tabs.length;
		for (int i = 0; i < tabs.length; i++) {
			SolarView.Tab tab = tabs[i];
			addWidget(new AstroButton(px + PAD + i * (tw + GAP), py + TAB_Y, tw, 18, Component.literal(tabLabel(tab)),
					AstroButton.Icon.NONE, () -> SolarView.tab == tab, () -> {
						SolarView.tab = tab;
						rebuildWidgets();
					}));
		}
	}

	private void buildContent() {
		List<Spec> specs = new ArrayList<>();
		switch (SolarView.tab) {
			case CAMERA -> {
				specs.add(new Spec("Free", () -> SolarView.mode == SolarView.Mode.FREE, () -> SolarView.setMode(SolarView.Mode.FREE)));
				specs.add(new Spec("Explore", () -> SolarView.mode == SolarView.Mode.EXPLORE, () -> SolarView.setMode(SolarView.Mode.EXPLORE)));
				if (SolarView.mode == SolarView.Mode.FREE) {
					specs.add(new Spec("Fokus Matahari", () -> false, () -> FreeCamera.focus(() -> SolarSystem.SUN_CENTER)));
					specs.add(new Spec("Fokus Bumi", () -> false, () -> {
						SolarSystem sys = SolarSystemClient.system();
						if (sys != null) {
							int earth = sys.earth().index();
							FreeCamera.focus(() -> SolarTools.planetVisual(earth));
						}
					}));
				}
			}
			case TOOLS -> {
				for (SolarView.Tool tool : SolarView.Tool.values()) {
					specs.add(new Spec(toolLabel(tool), () -> SolarView.tool == tool, () -> {
						SolarView.tool = tool;
						FreeLaunch.clear();
					}));
				}
			}
			case STYLE -> {
				for (SolarStyle style : SolarStyle.values()) {
					specs.add(new Spec(style.label, () -> SolarView.style == style, () -> SolarView.style = style));
				}
			}
			case KEYS -> {
			}
		}
		if (specs.isEmpty()) {
			return;
		}
		int bw = Math.min(118, (pw - 2 * PAD - GAP * (specs.size() - 1)) / specs.size());
		for (int i = 0; i < specs.size(); i++) {
			Spec spec = specs.get(i);
			addWidget(new AstroButton(px + PAD + i * (bw + GAP), py + BTN_Y, bw, 20, Component.literal(spec.label()),
					AstroButton.Icon.NONE, spec.selected(), () -> {
						spec.action().run();
						rebuildWidgets();
					}));
		}
	}

	private boolean inToolbar(double x, double y) {
		return x >= px && x < px + pw && y >= py && y < py + ph;
	}

	// ------------------------------------------------------------------ rendering

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		boolean overUi = inToolbar(mouseX, mouseY);
		FreeLaunch.updateHover(minecraft, mouseX, mouseY, overUi);
		if (overUi) {
			return;
		}
		String aim = FreeLaunch.label();
		if (aim != null) {
			g.text(font, aim, mouseX + 10, mouseY + 8, AstroButton.ACCENT);
		} else if (SolarView.tool != SolarView.Tool.NONE) {
			g.text(font, toolLabel(SolarView.tool), mouseX + 10, mouseY + 8, AstroButton.ACCENT);
		} else if (FreeLaunch.selected >= 0) {
			g.text(font, "Tarik untuk melempar", mouseX + 10, mouseY + 8, AstroButton.TEXT_DIM);
		}
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (inToolbar(event.x(), event.y())) {
			return true;
		}
		dragButton = event.button();
		pressX = event.x();
		pressY = event.y();
		moved = false;
		aimMode = dragButton == 0 && FreeLaunch.canAim();
		if (dragButton == 0 && SolarView.mode == SolarView.Mode.FREE && SolarView.tool == SolarView.Tool.LASER) {
			SolarView.laserHeld = true;
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (super.mouseDragged(event, dx, dy)) {
			return true;
		}
		if (dragButton < 0) {
			return false;
		}
		if (Math.abs(event.x() - pressX) + Math.abs(event.y() - pressY) > DRAG_SLOP) {
			moved = true;
		}
		if (SolarView.mode == SolarView.Mode.FREE) {
			if (dragButton == 0) {
				if (aimMode) {
					if (moved) {
						FreeLaunch.drag(event.x() - pressX, event.y() - pressY);
					}
				} else if (SolarView.tool != SolarView.Tool.LASER) {
					FreeCamera.orbit(dx, dy);
				}
			} else if (dragButton == 1) {
				FreeCamera.pan(dx, dy);
			}
		}
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		boolean handled = super.mouseReleased(event);
		if (dragButton == event.button()) {
			if (dragButton == 0) {
				SolarView.laserHeld = false;
				if (aimMode && moved) {
					FreeLaunch.release();
				} else if (!moved) {
					if (SolarView.tool == SolarView.Tool.NONE) {
						if (SolarView.mode == SolarView.Mode.FREE) {
							FreeLaunch.click(minecraft, event.x(), event.y());
						}
					} else {
						SolarTools.use(minecraft, event.x(), event.y());
					}
				}
			}
			aimMode = false;
			dragButton = -1;
		}
		return handled;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (super.mouseScrolled(x, y, scrollX, scrollY)) {
			return true;
		}
		if (SolarView.mode == SolarView.Mode.FREE && !inToolbar(x, y)) {
			FreeCamera.zoom(scrollY);
			return true;
		}
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == 256 && FreeLaunch.selected >= 0) { // GLFW ESC: deselect before leaving Free
			FreeLaunch.clear();
			return true;
		}
		if (SolarSystemClient.cameraKey().matches(event)) {
			SolarView.setMode(SolarView.mode == SolarView.Mode.FREE ? SolarView.Mode.EXPLORE : SolarView.Mode.FREE);
			rebuildWidgets();
			return true;
		}
		if (SolarSystemClient.toolbarKey().matches(event) && SolarView.mode == SolarView.Mode.EXPLORE) {
			onClose();
			return true;
		}
		SolarView.Tool[] hotkeys = {SolarView.Tool.LASER, SolarView.Tool.RING, SolarView.Tool.ASTEROID, SolarView.Tool.MOON};
		int index = event.key() - 49; // GLFW '1'
		if (index >= 0 && index < hotkeys.length) {
			SolarView.tool = SolarView.tool == hotkeys[index] ? SolarView.Tool.NONE : hotkeys[index];
			FreeLaunch.clear();
			rebuildWidgets();
			return true;
		}
		return super.keyPressed(event);
	}
}
