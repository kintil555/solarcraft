# Solar System (Fabric, Minecraft 26.2)

Every new world is a seed-generated solar system (cube Sun, cube planets, Earth from a reference cube map).

- Fly (creative, fast). Look at a planet, **hold R** to charge (aim = where you look), **release** to launch.
- A dotted preview shows the real N-body path (Sun + other planets pull on it). Orbit lines are live.
- Planets hitting the Sun reset to their normal orbit. State is saved in `solarsystem_state.txt` in the world.

Build: `./gradlew build` (Java 25). CI: GitHub Actions workflow included.
