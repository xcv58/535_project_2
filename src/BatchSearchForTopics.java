import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.plaf.basic.BasicInternalFrameTitlePane.TitlePaneLayout;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.xml.builders.TermQueryBuilder;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.apache.lucene.util.packed.PackedInts.Mutable;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.LockObtainFailedException;

import com.sun.corba.se.impl.encoding.OSFCodeSetRegistry.Entry;

/** Simple command-line based search demo. */
public class BatchSearchForTopics {

	private BatchSearchForTopics() {}

//	final static String[] stopWords = { "a", "able", "about", "across", "after", "all",
//		"almost", "also", "am", "among", "an", "and", "any", "are",
//		"as", "at", "be", "because", "been", "but", "by", "can",
//		"cannot", "could", "dear", "did", "do", "does", "either",
//		"else", "ever", "every", "for", "from", "get", "got", "had",
//		"has", "have", "he", "her", "hers", "him", "his", "how",
//		"however", "ƒi", "if", "in", "into", "is", "it", "its", "just",
//		"least", "let", "like", "likely", "may", "me", "might", "most",
//		"must", "my", "neither", "no", "nor", "not", "of", "off",
//		"often", "on", "only", "or", "other", "our", "own", "rather",
//		"said", "say", "says", "she", "should", "since", "so", "some",
//		"than", "that", "the", "their", "them", "then", "there",
//		"these", "they", "this", "tis", "to", "too", "twas", "us",
//		"wants", "was", "we", "were", "what", "when", "where", "which",
//		"while", "who", "whom", "why", "will", "with", "would", "yet",
//		"you", "your", "relevant" };	
	final static String[] stopWords = { "relevant", "relevance", "document", "documents", "identify", "identifies", "identification", "describe", "include", "included", "includes", "discuss", "discussing", "discussion", "discussions", "discussions", "focus", "interest", "item", "items", "involve", "involves", "involved", "any", "specific", "instance", "instances", "example", "examples", "information", "limited"};
	final static HashSet<String> stopWordSet = new HashSet<String>(Arrays.asList(stopWords));
		
	/** Simple command-line based search demo. */
	public static void main(String[] args) throws Exception {
		String usage =
				"Usage:\tjava BatchSearch [-index dir] [-simfn similarity] [-field f] [-queries file]";
		if (args.length > 0 && ("-h".equals(args[0]) || "-help".equals(args[0]))) {
			System.out.println(usage);
			System.out.println("Supported similarity functions:\ndefault: DefaultSimilary (tfidf)\n");
			System.exit(0);
		}

		String index = "index";
		String field = "contents";
		String queries = null;
		String simstring = "default";
		String addStrategy = "";

		for(int i = 0;i < args.length;i++) {
			if ("-index".equals(args[i])) {
				index = args[i+1];
				i++;
			} else if ("-field".equals(args[i])) {
				field = args[i+1];
				i++;
			} else if ("-queries".equals(args[i])) {
				queries = args[i+1];
				i++;
			} else if ("-simfn".equals(args[i])) {
				simstring = args[i+1];
				i++;
			} else if ("-strategy".equals(args[i])) {
				addStrategy = args[i+1];
				i++;
			}
		}

		Similarity simfn = null;
		if ("default".equals(simstring)) {
			simfn = new DefaultSimilarity();
		} else if ("bm25".equals(simstring)) {
			simfn = new BM25Similarity();
//			simfn = new BM25Similarity((float)1.2, (float)0.5);
		} else if ("dfr".equals(simstring)) {
			simfn = new DFRSimilarity(new BasicModelP(), new AfterEffectL(), new NormalizationH2());
		} else if ("lm".equals(simstring)) {
			simfn = new LMDirichletSimilarity();
		}
		if (simfn == null) {
			System.out.println(usage);
			System.out.println("Supported similarity functions:\ndefault: DefaultSimilary (tfidf)");
			System.out.println("bm25: BM25Similarity (standard parameters)");
			System.out.println("dfr: Divergence from Randomness model (PL2 variant)");
			System.out.println("lm: Language model, Dirichlet smoothing");
			System.exit(0);
		}
		
		IndexReader reader = DirectoryReader.open(FSDirectory.open(new File(index)));
		IndexSearcher searcher = new IndexSearcher(reader);
		searcher.setSimilarity(simfn);
		Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_41);
		
		BufferedReader in = null;
		if (queries != null) {
			in = new BufferedReader(new InputStreamReader(new FileInputStream(queries), "UTF-8"));
		} else {
			in = new BufferedReader(new InputStreamReader(new FileInputStream("queries"), "UTF-8"));
		}
		QueryParser parser = new QueryParser(Version.LUCENE_41, field, analyzer);
		Pattern numberReplacePattern = Pattern.compile("\\<num\\> Number:\\s*");
		Pattern replacePattern = Pattern.compile("(?<=[^A-Z])[:;\\*\\-\"\\.\\?,\\'/]|\\(.*?\\)");
		while (true) {
			
			//Handle one line for search
//			String line = in.readLine();
			String line = "";
			String endMarkString = "</top>";
			StringBuilder tmpStringBuilder = new StringBuilder();
			String number = "";
			String title = "";
			String desc = "";
			String narr = "";

			if (line == null || line.length() == -1) {
				break;
			}
			do {
				line = in.readLine();
				int anchor;
				if (line == null) {
					break;
				}
				if ((anchor = line.indexOf('<')) != -1) {
					if ((anchor = line.indexOf("<top>", anchor)) != -1) {
						continue;
					} else if ((anchor = line.indexOf("num>", anchor + 2)) != -1) {
						number = numberReplacePattern.matcher(line)
								.replaceFirst("").trim();
					} else if ((anchor = line.indexOf("title>", anchor + 2)) != -1) {
						tmpStringBuilder = new StringBuilder();
						tmpStringBuilder.append(line.substring(
								anchor + "title>".length()).trim());
					} else if ((anchor = line.indexOf("desc>", anchor + 2)) != -1) {
						title = tmpStringBuilder.toString();
						tmpStringBuilder = new StringBuilder();
						tmpStringBuilder.append(line.substring(
								anchor + "desc> Description:".length()).trim());
					} else if ((anchor = line.indexOf("narr>", anchor + 2)) != -1) {
						desc = tmpStringBuilder.toString();
						tmpStringBuilder = new StringBuilder();
						tmpStringBuilder.append(line.substring(
								anchor + "narr> Narrative:".length()).trim());
					} else if ((anchor = line.indexOf(endMarkString)) != -1) {
						narr = tmpStringBuilder.toString();
						break;
					}
				} else {
					tmpStringBuilder.append(line);
				}
				tmpStringBuilder.append(' ');
			} while (!line.equals(endMarkString));
			if (line == null) {
				break;
			}
			title = replacePattern.matcher(title).replaceAll(" ");
			desc = replacePattern.matcher(desc).replaceAll(" ");
			narr = removeIrrelevantString(narr);
			narr = replacePattern.matcher(narr).replaceAll(" ");
//			String queryString = title;
//			if (addStrategy.contains("d")) {
//				queryString += desc;
//				queryString += narr;
//			} 
//			if (addStrategy.contains("n")) {
//				queryString += narr;
//			}
//			HashMap<String, Integer> queryMap = new HashMap<String, Integer>();
//			String[] tokensStrings = queryString.split("\\s+");
////			System.out.println(tokensStrings.length);
//			for (String tmpString:tokensStrings) {
//				if (stopWordSet.contains(tmpString)) {
//					continue;
//				}
//				Integer tmpIndex = queryMap.get(tmpString);
//				if (tmpIndex == null) {
//					queryMap.put(tmpString, 1);
//				} else {
//					queryMap.put(tmpString, tmpIndex + 1);
//				}
//			}
//			List list = new LinkedList(queryMap.entrySet());
//			Collections.sort(list, new Comparator() {
//				public int compare(Object o1, Object o2) {
//					return ((Comparable) ((Map.Entry) (o2)).getValue())
//	                                       .compareTo(((Map.Entry) (o1)).getValue());
//				}
//			});
//			int topk = 100;
//			Iterator<Map.Entry<String, Integer>> e = list.iterator();
//			StringBuilder querySBuilder = new StringBuilder(title);
//			while(e.hasNext()) {
////				if (topk <= 0) {
////					break;
////				}
////				topk--;
//				Map.Entry<String, Integer> entry = e.next();
////				if (entry.getValue() == 1) {
////					break;
////				}
//				querySBuilder.append(entry.getKey() + " ");
//			}
//			queryString = querySBuilder.toString();
//			queryString = title + desc + narr;
//			Query query = parser.parse(queryString);
//			title = stopwordspPattern.matcher(title).replaceAll("");
//			desc = stopwordspPattern.matcher(desc).replaceAll("");
//			narr = stopwordspPattern.matcher(narr).replaceAll("");
			title = removeStopwords(title);
			desc = removeStopwords(desc);
			narr = removeStopwords(narr);
			
			BooleanQuery booleanQuery = new BooleanQuery();
			Query query = parser.parse(title);
			query.setBoost((float) 0.5);
			booleanQuery.add(query, BooleanClause.Occur.MUST);
			query = parser.parse(desc);
			query.setBoost((float) 0.35);
			booleanQuery.add(query, BooleanClause.Occur.MUST);
			if (!narr.matches("[\\s\\t]*")) {
				query = parser.parse(narr);
				query.setBoost((float) 0.15);
				booleanQuery.add(query, BooleanClause.Occur.MUST);
			}
//			query.setBoost((float) 0.5);
//			TermQueryBuilder tq = new TermQueryBuilder();
//			BooleanQuery booleanQuery = new BooleanQuery();
//			booleanQuery.add(query, BooleanClause.Occur.MUST);
//			booleanQuery.rewrite();
//			query.createWeight(searcher);
			
			doBatchSearch(in, searcher, number, booleanQuery, simstring);
//			doBatchSearch(in, searcher, number, query, simstring);
			
//			System.out.println(queryString);
			
//			System.out.println(number);
//			System.out.println(title);
//			System.out.println(desc);
//			System.out.println(narr);

			
//			String[] pair = line.split(" ", 2);
//			System.out.println("!!" + pair[0] + " \n"+ pair[1]);
//			System.out.println(line);
			
//			Query query = parser.parse(title + desc);
//			System.out.println(query.toString());
//			Query query = parser.parse(pair[1]);

//			doBatchSearch(in, searcher, pair[0], query, simstring);
		}
		reader.close();
	}

	/**
	 * This function performs a top-1000 search for the query as a basic TREC run.
	 */
	public static void doBatchSearch(BufferedReader in, IndexSearcher searcher, String qid, Query query, String runtag)	 
			throws IOException {

		// Collect enough docs to show 5 pages
		TopDocs results = searcher.search(query, 1000);
		ScoreDoc[] hits = results.scoreDocs;
		HashMap<String, String> seen = new HashMap<String, String>(1000);
		int numTotalHits = results.totalHits;
		
		int start = 0;
		int end = Math.min(numTotalHits, 1000);

		for (int i = start; i < end; i++) {
			Document doc = searcher.doc(hits[i].doc);
			String docno = doc.get("docno");
			// There are duplicate document numbers in the FR collection, so only output a given
			// docno once.
			if (seen.containsKey(docno)) {
				continue;
			}
			seen.put(docno, docno);
			System.out.println(qid+" Q0 "+docno+" "+i+" "+hits[i].score+" "+runtag);
		}
	}
	
	private static String removeIrrelevantString (String s) {
		s = s.replaceAll("\\(.*?\\)", "");
		String regexString = "[^\\.]*?((no.{1,5}relevant)|(irrelevant))[^\\.]*?(\\.|$)";
		Pattern removeIrrelevantPattern = Pattern.compile(regexString);
		Matcher m = removeIrrelevantPattern.matcher(s);
//		while (m.find()) {
//			String tmpString = m.group();
//			System.out.println(tmpString);
//		}
		String result = removeIrrelevantPattern.matcher(s).replaceAll("");
		return result;
//		return null;
	}
	private static String removeStopwords (String s) {
		String[] tmpStrings = s.split("[\\s\\t]+");
		StringBuilder sb = new StringBuilder();
		for (String tmpString:tmpStrings) {
			tmpString = tmpString.toLowerCase();
			if (!stopWordSet.contains(tmpString)) {
				sb.append(tmpString + " ");
			}
		}
		String result = sb.toString().trim();
		return result;
//		return null;
	}
}

