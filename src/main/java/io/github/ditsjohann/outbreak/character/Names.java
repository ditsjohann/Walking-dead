package io.github.ditsjohann.outbreak.character;

import java.util.List;

import net.minecraft.util.RandomSource;

/** Names for the "random name" button: Dutch, and plenty of Groningers among them. */
public final class Names {
	private static final List<String> FIRST = List.of(
		"Jan", "Sanne", "Daan", "Lotte", "Bram", "Femke", "Henk", "Anouk", "Sem", "Eva", "Pieter", "Lieke", "Ruben", "Iris",
		"Thijs", "Noor", "Gerrit", "Wietske", "Harm", "Geert", "Anne", "Klaas", "Roelf", "Grietje", "Sietse", "Marieke",
		"Jelle", "Fenna", "Luuk", "Tess", "Willem", "Annelies", "Kees", "Jildou", "Hidde", "Romy", "Albert", "Hennie");
	private static final List<String> LAST = List.of(
		"de Vries", "Jansen", "Bakker", "Visser", "Smit", "Meijer", "de Boer", "Mulder", "Bos", "Hoekstra", "Dijkstra",
		"Postma", "Huizinga", "Kuipers", "van der Veen", "Veenstra", "Brouwer", "Wiersma", "Zijlstra", "Boersma", "Kramer",
		"Tammenga", "Oosterhuis", "Nijboer", "Kloosterman", "Pruim", "Stol", "Venema", "Wubbels", "Hofstede");

	private Names() {
	}

	public static String random(RandomSource random) {
		return FIRST.get(random.nextInt(FIRST.size())) + " " + LAST.get(random.nextInt(LAST.size()));
	}
}
