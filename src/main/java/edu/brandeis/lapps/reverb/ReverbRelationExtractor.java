package edu.brandeis.lapps.reverb;


import edu.washington.cs.knowitall.commonlib.Range;
import edu.washington.cs.knowitall.extractor.ReVerbExtractor;
import edu.washington.cs.knowitall.nlp.ChunkedSentence;
import edu.washington.cs.knowitall.nlp.extraction.ChunkedBinaryExtraction;
import edu.washington.cs.knowitall.nlp.extraction.ChunkedExtraction;
import edu.washington.cs.knowitall.util.DefaultObjects;
import org.lappsgrid.api.WebService;
import org.lappsgrid.metadata.IOSpecification;
import org.lappsgrid.metadata.ServiceMetadata;
import org.lappsgrid.serialization.Data;
import org.lappsgrid.serialization.Serializer;
import org.lappsgrid.serialization.lif.Annotation;
import org.lappsgrid.serialization.lif.Container;
import org.lappsgrid.serialization.lif.View;
import org.lappsgrid.vocabulary.Features;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.*;

import static org.lappsgrid.discriminator.Discriminators.Uri;


/**
 * @author krim@brandeis.edu
 *
 */

public class ReverbRelationExtractor implements WebService {

    public static final String LIF_SCHEMA = "http://vocab.lappsgrid.org/schema/container-schema-1.0.0.json";

    private static final Logger log = LoggerFactory.getLogger(ReverbRelationExtractor.class);
    private String metadataString;

    /**
     * Default constructor.
     * Only loads up metadata json file.
     * These json files are generated from @annotation of this class by mvn compilation
     */
    public ReverbRelationExtractor() {
        try {
            metadataString = loadMetadata();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Get version fro POM.
     * The trick is version.properties file copied and expanded during compilation
     */
    protected String getVersion() {
        String path = "/version.properties";
        InputStream stream = getClass().getResourceAsStream(path);
        if (stream == null)
            return "0.0.0.UNKNOWN";
        Properties props = new Properties();
        try {
            props.load(stream);
            stream.close();
            return (String) props.get("version");
        } catch (IOException e) {
            return "0.0.0.UNKNOWN";
        }
    }

    /**
     * Generates a LEDS json with error message.
     */
    private String errorLEDS(String message) {
        return new Data<>(Uri.ERROR, message).asJson();
    }

    @Override
    /**
     * This is default execute: takes a json string, wrap it as a LIF,
     * run real execute() with the LIF container
     */
    public String execute(String input) {

        if (input == null) {
            log.error("Input is null");
            return errorLEDS("Input is null");
        }
        Data leds;
        try {
            leds = Serializer.parse(input, Data.class);
        } catch (Exception e) {
            // Serializer#parse will throw a Unchecked groovy exception
            // if the input is not a well-formed json
            leds = new Data();
            leds.setDiscriminator(Uri.TEXT);
            leds.setPayload(input);
        }

        final String discriminator = leds.getDiscriminator();
        Container lif;

        switch (discriminator) {
            case Uri.ERROR:
                log.info("Input contains ERROR");
                return input;
            case Uri.JSON_LD:
            case Uri.LIF:
                log.info("Input contains LIF");
                lif = new Container((Map) leds.getPayload());
                break;
            case Uri.TEXT:
                log.info("Input contains TEXT");
                lif = new Container();
                // TODO: 5/9/18  fix url when it settles in
//                lif.setSchema(LIF_SCHEMA);
                lif.setText((String) leds.getPayload());
                lif.setLanguage("en");
                break;
            default:
                String unsupported = String.format(
                        "Unsupported discriminator type: %s", discriminator);
                log.error(unsupported);
                return errorLEDS(unsupported);
        }

        try {
            return execute(lif);
        } catch (Throwable th) {
            th.printStackTrace();
            log.error("Error processing input", th.toString());
            return errorLEDS(String.format(
                    "Error processing input: %s", th.toString()));
        }
    }

    private String execute(Container lif) throws Exception {

        String inputText = lif.getText();
        if (inputText.length() > 0) {
            log.info("Loading sentence splitter && chunker");
            String[] sentences = DefaultObjects.getDefaultSentenceDetector().sentDetect(inputText);
            log.info("Done!");

            log.info("Loading relation extractor");
            log.info("Done!");

            View view = lif.newView("v00" + (lif.getViews().size() + 1));

            String serviceName = this.getClass().getName();
            view.addContains(Uri.TOKEN, String.format("%s:%s", serviceName, getVersion()),
                    "tokenization:reverb");

            int sidx = 1;
            int soffset = 0;
            for (String sentence : sentences) {
                soffset = inputText.indexOf(sentence, soffset);
                // We did sent-split first, using the same library. So we believe this "sentence" string only contains one sentence.
                Iterator<ChunkedSentence> chunkedSentenceIterator = DefaultObjects.getDefaultSentenceReader(new StringReader(sentence)).getSentences().iterator();
                if (!chunkedSentenceIterator.hasNext()) {
                    continue;
                }
                ChunkedSentence sent = chunkedSentenceIterator.next();
                int midx = 1;
                int ridx = 1;
                List<int[]> tokenSpans = addTokenAnnotations(sent, sidx, soffset, view);

                // TODO: 2016-05-19 01:19:38EDT  By using ConfidenceFunction, we can filter out low-confident relations. Should we?
                Iterator<ChunkedBinaryExtraction> extracted = extractRelations(sent, view, serviceName);

                while (extracted.hasNext()) {
                    ChunkedBinaryExtraction extr = extracted.next();
                    ChunkedExtraction rel = extr.getRelation();
                    // add each as markable, and remember markable IDs
                    String relMid = addMarkbleAnnotation(sidx, midx++, tokenSpans, rel, view);
                    String arg1Mid = addMarkbleAnnotation(sidx, midx++, tokenSpans, extr.getArgument1(), view);
                    String arg2Mid = addMarkbleAnnotation(sidx, midx++, tokenSpans, extr.getArgument2(), view);

                    // use remembered markable IDs to add relation annotation
                    Annotation relation = view.newAnnotation(makeID("rel_", sidx, ridx++), Uri.GENERIC_RELATION);
                    relation.addFeature(Features.GenericRelation.ARGUMENTS, Arrays.toString(new String[]{arg1Mid, arg2Mid}));
                    relation.addFeature(Features.GenericRelation.RELATION, relMid);
                    relation.addFeature(Features.GenericRelation.LABEL, rel.getText());
                }
                sidx++;
                soffset += sentence.length();
            }
        }
        Data<Container> data = new Data<>(Uri.LIF, lif);
        return Serializer.toJson(data);
    }

    /**
     * With given a chunked sentence, extract relations using reverb library.
     * If any relation is found, add contains information to the container.
     */
    private Iterator<ChunkedBinaryExtraction> extractRelations(ChunkedSentence sent, View view, String serviceName) {
        ReVerbExtractor reverb = new ReVerbExtractor();
        Iterator<ChunkedBinaryExtraction> extracted = reverb.extract(sent).iterator();
        if (extracted.hasNext()) {
            view.addContains(Uri.MARKABLE, String.format("%s:%s", serviceName, getVersion()),
                    "marakbles:reverb");
            view.addContains(Uri.GENERIC_RELATION, String.format("%s:%s", serviceName, getVersion()),
                    "relations:reverb");
        }
        return extracted;
    }

    /**
     * With given indices and extracted chunk, add a single Markable annotation to the view
     */
    private String addMarkbleAnnotation(int sidx, int midx, List<int[]> tokenSpans, ChunkedExtraction markable, View view) {
        String markableId = makeID("m_", sidx, midx);
        final int firstTokenIdx = markable.getRange().getStart();
        int lastTokenIdx = markable.getRange().getEnd() - 1;
        int charOffsetStart = tokenSpans.get(firstTokenIdx)[0];
        int charOffsetEnd = tokenSpans.get(lastTokenIdx)[1];
        Annotation markableAnnotation = view.newAnnotation(markableId, Uri.MARKABLE, charOffsetStart, charOffsetEnd);
        return markableId;
    }

    /**
     * With given a chunked sentence, add all tokens to the view
     */
    private List<int[]> addTokenAnnotations(ChunkedSentence sent, int sidx, int soffset, View view) {
        int tidx = 1;
        List<int[]> tokenSpans = new ArrayList<>();
        for (String token : sent.getTokens()) {
            Range curTokSpan = sent.getOffsets().get(tidx - 1);
            int start = curTokSpan.getStart() + soffset; int end = curTokSpan.getEnd() + soffset;
            tokenSpans.add(new int[]{start, end});
            Annotation tok = view.newAnnotation(
                    makeID("tk_", sidx, tidx++), Uri.TOKEN,
                    start, end);
            tok.addFeature("word", token);
        }
        return tokenSpans;
    }

    /**
     * Load metadata from compiler generated json files.
     * @throws IOException when metadata json file was not found.
     */
    public String loadMetadata() throws IOException {


        ServiceMetadata metadata = new ServiceMetadata();
        // TODO: 4/22/18 fix url when it settles in
        metadata.setSchema("http://vocab.lappsgrid.org/schema/metadata-schema-1.1.0.json");
        metadata.setVendor("http://www.cs.brandeis.edu/");
        metadata.setLicense(Uri.APACHE2);
        // TODO: 5/15/2018 write better description
        metadata.setDescription("ReVerb Relation Extractor");
        // TODO: 5/15/2018 find full string for "any"
        metadata.setAllow("any");
        metadata.setVersion(this.getVersion());
        metadata.setName(this.getClass().getName());

        IOSpecification required = new IOSpecification();
        required.addLanguage("en");
        required.setEncoding("UTF-8");
        required.addFormat(Uri.TEXT);
        required.addFormat(Uri.LIF);
        metadata.setRequires(required);

        IOSpecification produces = new IOSpecification();
        produces.addLanguage("en");
        produces.setEncoding("UTF-8");
        produces.addFormat(Uri.LIF);
        produces.addAnnotations(Uri.TOKEN, Uri.GENERIC_RELATION, Uri.MARKABLE);
        metadata.setProduces(produces);

        return new Data<>(Uri.META, metadata).asJson();
    }

    @Override
    public String getMetadata() {
        return this.metadataString;
    }

    /* ================= some helpers ================== */
    /**
     * Generates ID string
     */
    private static String makeID(String prefix, int sidx, int tidx) {
        return String.format("%s%d_%d", prefix, sidx, tidx);
    }

    /**
     * Generates ID string
     */
    private static String makeID(String prefix, int id) {
        return String.format("%s%d", prefix, id);

    }

}

