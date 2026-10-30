package io.github.ditsjohann.outbreak.world;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.ditsjohann.outbreak.Outbreak;
import io.github.ditsjohann.outbreak.character.StartPoint;
import io.github.ditsjohann.outbreak.sandbox.Sandbox;
import io.github.ditsjohann.outbreak.sandbox.SandboxSettings;

import net.minecraft.core.BlockPos;

/**
 * What Groningen's buildings are, from OpenStreetMap (tools/data/make_places.py writes the list:
 * every shop, pharmacy, hospital, school, police station...): so a cupboard in a pharmacy has
 * medicine in it, the hospital is full of walkers in scrubs, and the police station has guns. Only on
 * the Groningen map; the boxes are projected onto it with the world's own Arnis box.
 */
public final class Places {
	/** What a building is: its loot table, whether the dead are thick there, and what they were wearing. */
	public enum Kind {
		MILITARY(true, 11),
		HOSPITAL(true, 4, 20, 5, 21),
		CLINIC(false, 4, 5, 20),
		PHARMACY(false),
		POLICE(true, 3, 19),
		FIRE_STATION(false, 6),
		FUEL(false),
		SUPERMARKET(true),
		GROCERY(false),
		HARDWARE(false, 6, 22),
		BIKE_SHOP(false, 12),
		CAR_SHOP(false, 6),
		CLOTHES(false),
		ELECTRONICS(false),
		BOOKS(false, 7),
		RESTAURANT(false, 15),
		BAR(false),
		SCHOOL(true, 7, 23),
		CHURCH(false, 10),
		HOTEL(false),
		GYM(false, 9, 8),
		OFFICE(false, 2, 18),
		INDUSTRIAL(false, 6, 22),
		STORE(false);

		/** Walkers turn up here whatever the rest of the area is like. */
		public final boolean crowded;
		/** Walker skins that belong here (their outfits: see tools/walkers/make_walkers.py). */
		public final int[] skins;

		Kind(boolean crowded, int... skins) {
			this.crowded = crowded;
			this.skins = skins;
		}

		public String id() {
			return this.name().toLowerCase(Locale.ROOT);
		}

		/** Its container loot table (data/outbreak/loot_table/containers/place_&lt;kind&gt;.json). */
		public String lootTable() {
			return "containers/place_" + this.id();
		}
	}

	private record Place(Kind kind, double minLat, double minLng, double maxLat, double maxLng) {
	}

	/** A place projected onto the map, in blocks. */
	private record Box(Kind kind, int minX, int minZ, int maxX, int maxZ) {
		long area() {
			return (long) (this.maxX - this.minX + 1) * (this.maxZ - this.minZ + 1);
		}
	}

	private static final int CELL = 64;
	private static @Nullable List<Place> loaded;
	private static @Nullable String indexedFor;
	private static Map<Long, List<Box>> index = Map.of();

	private Places() {
	}

	/** The kind of building at this spot, or null (a house, the street, or not the Groningen map). */
	public static @Nullable Kind at(BlockPos pos) {
		Map<Long, List<Box>> cells = index(Sandbox.current());
		List<Box> here = cells.get(cell(Math.floorDiv(pos.getX(), CELL), Math.floorDiv(pos.getZ(), CELL)));
		if (here == null) {
			return null;
		}

		Box best = null;
		for (Box box : here) {
			if (pos.getX() >= box.minX && pos.getX() <= box.maxX && pos.getZ() >= box.minZ && pos.getZ() <= box.maxZ
				&& (best == null || box.area() < best.area())) {
				best = box;
			}
		}

		return best == null ? null : best.kind;
	}

	/** How many places are known (for the map in use). */
	public static int count() {
		List<Place> places = load();
		return Sandbox.current().map() == SandboxSettings.MapKind.GRONINGEN ? places.size() : 0;
	}

	private static long cell(int x, int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}

	private static synchronized Map<Long, List<Box>> index(SandboxSettings settings) {
		String key = settings.map() + " " + settings.bbox();
		if (key.equals(indexedFor)) {
			return index;
		}

		indexedFor = key;
		index = new HashMap<>();
		double[] bbox = StartPoint.parseBbox(settings.bbox());
		if (settings.map() != SandboxSettings.MapKind.GRONINGEN || bbox == null) {
			return index;
		}

		for (Place place : load()) {
			// North is up on the map: the larger latitude gives the smaller z.
			BlockPos a = StartPoint.project(bbox, place.maxLat, place.minLng);
			BlockPos b = StartPoint.project(bbox, place.minLat, place.maxLng);
			Box box = new Box(place.kind, Math.min(a.getX(), b.getX()), Math.min(a.getZ(), b.getZ()), Math.max(a.getX(), b.getX()), Math.max(a.getZ(), b.getZ()));
			for (int cx = Math.floorDiv(box.minX, CELL); cx <= Math.floorDiv(box.maxX, CELL); cx++) {
				for (int cz = Math.floorDiv(box.minZ, CELL); cz <= Math.floorDiv(box.maxZ, CELL); cz++) {
					index.computeIfAbsent(cell(cx, cz), k -> new ArrayList<>()).add(box);
				}
			}
		}

		return index;
	}

	private static synchronized List<Place> load() {
		if (loaded != null) {
			return loaded;
		}

		List<Place> places = new ArrayList<>();
		try (InputStream in = Places.class.getResourceAsStream("/data/outbreak/places/groningen.json")) {
			if (in != null) {
				JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
				for (JsonElement element : json.getAsJsonArray("places")) {
					JsonArray row = element.getAsJsonArray();
					try {
						Kind kind = Kind.valueOf(row.get(0).getAsString().toUpperCase(Locale.ROOT));
						places.add(new Place(kind, row.get(1).getAsDouble(), row.get(2).getAsDouble(), row.get(3).getAsDouble(), row.get(4).getAsDouble()));
					} catch (IllegalArgumentException e) {
						// A kind this version doesn't know: skip it.
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			Outbreak.LOGGER.warn("Could not read the map of Groningen's places", e);
		}

		loaded = places;
		return places;
	}
}
