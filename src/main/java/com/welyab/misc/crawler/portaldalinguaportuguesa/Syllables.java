package com.welyab.misc.crawler.portaldalinguaportuguesa;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

public class Syllables {

    public static void main(String[] args) {
	System.setProperty(
		"webdriver.chrome.driver",
		getDriverPath().toString()
	);

	WebDriver driver = new ChromeDriver();
	// WebDriver driver = new HtmlUnitDriver(true);
	try (AutoCloseable closeable = () -> driver.close();) {

	    List<String> letterLinks = getLetterLinks(driver);
	    if (letterLinks.isEmpty()) {
		System.err.print("Can't find html table tab container for letter links");
		return;
	    }

	    System.out.println("Letter links found:");
	    letterLinks.forEach(System.out::println);

	    System.out.println();

	    for (int i = 0; i < letterLinks.size(); i++) {
		String link = letterLinks.get(i);
		do {
		    crawler(driver, link);
		    link = getNextPage(driver);
		} while (link != null);
	    }

	} catch (Exception e) {
	    throw new RuntimeException(e);
	}
    }

    private static void crawler(WebDriver driver, String url) {
	driver.get(url);
	String pageLetter = getPageLetter(driver);
	String currentPageId = getCurrentPageIdentification(driver);

	if (pageLetter == null || pageLetter.trim().isEmpty()) {
	    System.err.println("Can't find current page letter");
	    return;
	}

	if (currentPageId == null || currentPageId.trim().isEmpty()) {
	    System.err.print("Can't find current page id");
	}

	System.out.printf("Processing letter %s - %s%n", pageLetter, currentPageId);

	Path file = getFilePathToSave(pageLetter, currentPageId);

	if (Files.exists(file)) {
	    System.out.println("Already processed: " + file);
	    return;
	}

	List<WordInfo> words = getWords(driver);

	try (BufferedWriter writer = Files.newBufferedWriter(file)) {
	    for (WordInfo info : words) {
		writer.write(info.word);
		writer.write("\t");
		writer.write(info.category);
		writer.write("\t");
		writer.write(info.syllables);
		writer.write("\n");
	    }
	} catch (IOException e) {
	    throw new UncheckedIOException(e);
	}

	System.out.println("Saved to file: " + file);
    }

    private static String getNextPage(WebDriver driver) {
	WebElement pTag = driver
		.findElements(By.tagName("p"))
		.stream()
		.filter(p -> p.getAttribute("style").equals("color: rgb(102, 102, 102);"))
		.findFirst()
		.orElse(null);

	if (pTag == null) {
	    return null;
	}

	WebElement aTag = pTag
		.findElements(By.tagName("a"))
		.stream()
		.filter(a -> a.getText().contains("seguintes"))
		.findFirst()
		.orElse(null);

	if (aTag == null) {
	    return null;
	}

	return aTag.getAttribute("href");
    }

    private static List<WordInfo> getWords(WebDriver driver) {
	List<WordInfo> list = new ArrayList<>();

	WebElement table = driver
		.findElements(By.tagName("table"))
		.stream()
		.filter(t -> t.getAttribute("id") != null)
		.filter(t -> t.getAttribute("id").equals("rollovertable"))
		.filter(t -> t.getAttribute("name") != null)
		.filter(t -> t.getAttribute("name").equals("rollovertable"))
		.findFirst()
		.orElse(null);

	if (table == null) {
	    return Collections.emptyList();
	}

	List<WebElement> trs = table.findElements(By.tagName("tr"));
	for (int i = 0; i < trs.size(); i++) {
	    WebElement tr = trs.get(i);
	    List<WebElement> tds = tr.findElements(By.tagName("td"));

	    if (tds.size() != 2) {
		continue;
	    }

	    WebElement tdWord = tds.get(0);
	    WebElement tdSyllabs = tds.get(1);

	    if (!tdWord.getAttribute("title").equals("Palavra")) {
		continue;
	    }

	    List<String> syllabsDividers = tdSyllabs
		    .findElements(By.cssSelector("b, span"))
		    .stream()
		    .filter(
			    e -> e.getTagName().equals("b")
				    || e.getAttribute("style").equals("nobreaksyllable")
		    )
		    .map(e -> e.getText())
		    .collect(Collectors.toList());

	    String word = tdWord.getText().replaceAll("\\(.*", "").trim();
	    String syllabsTd = tdSyllabs.getText().trim();
	    String syllabs = standardizeSyllabs(syllabsTd, syllabsDividers);

	    if (syllabs == null) {
		System.err.println(String.format(
			"Inconsistence in word '%s', syllabed %s, with dividers %s",
			word, syllabsTd, syllabsDividers.toString()
		));
	    }

	    WordInfo info = new WordInfo(
		    word,
		    tdWord.getText().replaceAll(".+\\(", "").replaceAll("\\).*", ""),
		    syllabs
	    );

	    list.add(info);
	}

	return list;
    }

    private static String standardizeSyllabs(String word, List<String> diveders) {
	String[] syllabs = word.split("·");

	if (syllabs.length == 1 && diveders.size() == 1) {
	    return "[" + syllabs[0] + "]";
	}

	StringBuilder builder = new StringBuilder(word.length() + 4);

	for (int i = 0; i < syllabs.length; i++) {
	    if (i < diveders.size() && syllabs[i].equals(diveders.get(i))) {
		builder.append("[").append(syllabs[i]).append("]");
	    } else {
		builder.append(syllabs[i]);
	    }
	    if (i < syllabs.length - 1) {
		builder.append("|");
	    }
	}

	return builder.toString();
    }

    private static class WordInfo {
	final String word;
	final String category;
	final String syllables;

	public WordInfo(String word, String category, String syllables) {
	    this.word = word;
	    this.category = category;
	    this.syllables = syllables;
	}

	@Override
	public String toString() {
	    return String.format("%s, %s, %s", word, category, syllables);
	}
    }

    private static String getCurrentPageIdentification(WebDriver driver) {
	WebElement pTag = driver
		.findElements(By.tagName("p"))
		.stream()
		.filter(p -> p.getAttribute("style").equals("color: rgb(102, 102, 102);"))
		.findFirst()
		.orElse(null);

	if (pTag == null) {
	    return null;
	}

	String text = pTag.getText().trim();
	return Arrays
		.stream(text.split("\\D"))
		.filter(s -> !s.isEmpty())
		.reduce((s1, s2) -> String.format("%s_%s", s1, s2))
		.orElse(null);

    }

    private static Path getFilePathToSave(String letter, String currentPage) {
	Path dir = Paths.get(System.getProperty("user.home"))
		.resolve("crawler-portaldalinguaportuguesa-org")
		.resolve(letter);

	if (Files.notExists(dir)) {
	    try {
		Files.createDirectories(dir);
	    } catch (IOException e) {
		throw new RuntimeException(String.format(
			"Can't create the directory: %s",
			dir
		));
	    }
	}

	return dir.resolve(currentPage + ".txt");
    }

    private static String getPageLetter(WebDriver driver) {
	WebElement pTag = driver
		.findElements(By.tagName("p"))
		.stream()
		.filter(p -> p.getAttribute("style").equals("color: rgb(102, 102, 102);"))
		.findFirst()
		.orElse(null);

	if (pTag == null) {
	    return null;
	}

	WebElement iTag = pTag.findElement(By.tagName("i"));
	if (iTag == null) {
	    return null;
	}

	return iTag.getText().trim();
    }

    private static List<String> getLetterLinks(WebDriver driver) {
	driver.get("http://www.portaldalinguaportuguesa.org/index.php?action=syllables&act=list");
	WebElement table = driver
		.findElements(By.tagName("table"))
		.stream()
		.filter(e -> e.getAttribute("name") != null)
		.filter(e -> e.getAttribute("name").equals("maintable"))
		.filter(e -> e.getAttribute("id") != null)
		.filter(e -> e.getAttribute("id").equals("maintable"))
		.findFirst()
		.orElse(null);

	if (table == null) {
	    return Collections.emptyList();
	}

	return table
		.findElements(By.tagName("a"))
		.stream()
		.map(a -> a.getAttribute("href"))
		.collect(Collectors.toList());
    }

    private static Path getDriverPath() {
	String osArch = System.getProperty("os.arch").contains("64")
		? "64"
		: "32";
	String osName = System.getProperty("os.name").toLowerCase().contains("linux")
		? "linux"
		: "";

	return Paths.get(System.getProperty("user.dir"))
		.resolve("src/main/resources/selenium/driver")
		.resolve(String.format("chromedriver_2.38_%sbits_%s", osArch, osName));
    }
}
