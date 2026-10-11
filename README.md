# Solar System (Fabric, Minecraft 26.2)

Every new world is a seed-generated solar system (cube Sun, cube planets, Earth from a reference cube map).

- Fly (creative, fast). Look at a planet, **hold R** to charge (aim = where you look), **release** to launch.
- A dotted preview shows the real N-body path (Sun + other planets pull on it). Orbit lines are live.
- Planets hitting the Sun reset to their normal orbit. State is saved in `solarsystem_state.txt` in the world.

Build: `./gradlew build` (Java 25). CI: GitHub Actions workflow included.

## Camera modes, toolbar and solar style

Requires the [UI Lib](https://github.com/DAQEM/UILib) mod (the build uses `libs/uilib-fabric-21_1_2.jar`).

**Toolbar** (bottom of the screen): click the chevron to expand it. Tabs: *Camera Mode*, *Tools*, *Keybind*, *Solar Style*.
Press `G` to open the toolbar in Explore mode (frees the cursor), `V` to switch Free / Explore.

**Camera modes**
- *Explore* - the normal flying camera.
- *Free* - orbit camera with a free cursor: scroll = zoom, hold right mouse = pan, hold left mouse = orbit.
  `ESC` leaves Free mode. The Camera Mode tab can focus the Sun or Earth.

**Solar style** (visual only, the physics always uses the real orbits)
- *Real* - wide spacing, planets of clearly different sizes, particle rings on gas/ice giants.
- *Diperbesar* - the original look.
- *Simple* - compact, evenly spaced orbits, uniform small planets.

**Tools** (select a tool, then left-click in the world; keys `1`-`4`)
- *Laser* - fires a beam; a hit planet gets a small push (sent to the server, changes its orbit).
- *Planet Ring* - toggles a particle ring on the planet you click.
- *Asteroid* - releases an asteroid that orbits the Sun and disappears when it hits the Sun or a planet.
- *Moon* - adds a moon (max 4) orbiting the clicked planet.

Rings, moons and asteroids are client-side decorations: they are not saved and not shared with other players.
