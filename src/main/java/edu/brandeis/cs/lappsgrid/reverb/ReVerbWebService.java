package edu.brandeis.cs.lappsgrid.reverb;

import edu.washington.cs.knowitall.commonlib.Range;
import edu.washington.cs.knowitall.extractor.ReVerbExtractor;
import edu.washington.cs.knowitall.nlp.ChunkedSentence;
import edu.washington.cs.knowitall.nlp.ChunkedSentenceReader;
import edu.washington.cs.knowitall.nlp.extraction.ChunkedBinaryExtraction;
import edu.washington.cs.knowitall.nlp.extraction.ChunkedExtraction;
import edu.washington.cs.knowitall.util.DefaultObjects;
import org.apache.xerces.impl.io.UTF8Reader;
import org.lappsgrid.api.WebService;
import org.lappsgrid.metadata.ServiceMetadata;
import org.lappsgrid.serialization.Data;
import org.lappsgrid.serialization.Serializer;
import org.lappsgrid.serialization.lif.Annotation;
import org.lappsgrid.serialization.lif.Container;
import org.lappsgrid.serialization.lif.View;
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

@org.lappsgrid.annotations.ServiceMetadata(
        vendor = "http://www.cs.brandeis.edu/",
        license = "apache2",
        allow = "any",
        language = { "en" },
        description = "ReVerb Relation Extractor",
        requires_format = { "text", "lif" },
        produces_format = { "lif" },
        produces = { "generic-relation", "markable", "token" }
)
public class ReVerbWebService implements WebService {

    protected static final Logger log
            = LoggerFactory.getLogger(ReVerbWebService.class);

    private String metadata;

    /**
     * Default constructor.
     * By default, initiating any service will load up the tokenizer for English
     * and distributional semantics model, which will be used globally.
     */
    public ReVerbWebService() {
        try {
            loadMetadata();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Get version from metadata
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
     * @param message
     * @return
     */
    protected String errorLEDS(String message) {
        return new Data<>(Uri.ERROR, message).asJson();
    }

    @Override
    /**
     * This is default execute: takes a json, wrap it as a LIF, run modules
     */
    public String execute(String input) {

        if (input == null) {
            log.error("Input is null");
            return errorLEDS("Input is null");
        }
        Data leds;
        leds = Serializer.parse(input, Data.class);
        // Serializer will catch any json exception and return null in that case
        if (leds ==  null) {
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

    /**
     * This will be overridden for each module
     * TODO 151103 need a specific exception class
     */
    public String execute(Container json) throws Exception {

        String inputText = json.getText();
        log.info("Loading sentence splitter && chunker");
        ChunkedSentenceReader sentReader = DefaultObjects.getDefaultSentenceReader(new StringReader(inputText));

        log.info("Loading relation extractor");
        ReVerbExtractor reverb = new ReVerbExtractor();

        View view = json.newView();

        String serviceName = this.getClass().getName();
        view.addContains(Uri.TOKEN, String.format("%s:%s", serviceName, getVersion()),
                "tokenized:reverb");

        int sid = 1;
        for (ChunkedSentence sent : sentReader.getSentences()) {
            int tid = 1;
            int mid = 1;
            int rid = 1;
            List<int[]> tokenSpans = addTokenAnnotations(sent, sid, view);

            // TODO: 2016-05-19 01:19:38EDT  By using ConfidenceFunction, we can filter out low-confident relations. Should we?
            Iterator<ChunkedBinaryExtraction> extracted = hasRelation(reverb, view, serviceName, sent);
            
            while (extracted.hasNext()) {
                ChunkedBinaryExtraction extr = extracted.next();
                ChunkedExtraction rel = extr.getRelation();
                String relMid = addMarkbleAnnotation(sid, mid++, tokenSpans, rel, view);
                String arg1Mid = addMarkbleAnnotation(sid, mid++, tokenSpans, extr.getArgument1(), view);
                String arg2Mid = addMarkbleAnnotation(sid, mid++, tokenSpans, extr.getArgument2(), view);

                Annotation relation = view.newAnnotation(makeID("rel_", sid, rid++), Uri.GENERIC_RELATION);
                relation.addFeature("arguments", Arrays.toString(new String[]{arg1Mid, arg2Mid}));
                relation.addFeature("relation", relMid);
                relation.addFeature("label", rel.getText());
            }
            sid++;
        }
        Data<Container> data = new Data<>(Uri.LIF, json);
        return Serializer.toJson(data);
    }

    private String addMarkbleAnnotation(int sid, int mid, List<int[]> tokenSpans, ChunkedExtraction markable, View view) {
        String markableId = makeID("m_", sid, mid);
        final int firstTokenIdx = markable.getRange().getStart();
        int lastTokenIdx = markable.getRange().getEnd() - 1;
        int charOffsetStart = tokenSpans.get(firstTokenIdx)[0];
        int charOffsetEnd = tokenSpans.get(lastTokenIdx)[1];
        Annotation markableAnnotation = view.newAnnotation(markableId, Uri.MARKABLE, charOffsetStart, charOffsetEnd);
        List<String> targetTokens = new ArrayList<>();
        for (int i = firstTokenIdx; i <= lastTokenIdx; i++) {
            targetTokens.add(makeID("tk_", sid, i + 1));
        }
        markableAnnotation.addFeature("targets", targetTokens.toString());
        return markableId;
    }

    private Iterator<ChunkedBinaryExtraction> hasRelation(ReVerbExtractor reverb, View view, String serviceName, ChunkedSentence sent) {
        Iterator<ChunkedBinaryExtraction> extracted = reverb.extract(sent).iterator();
        if (extracted.hasNext()) {
            view.addContains(Uri.MARKABLE, String.format("%s:%s", serviceName, getVersion()),
                    "marakbles:reverb");
            view.addContains(Uri.GENERIC_RELATION, String.format("%s:%s", serviceName, getVersion()),
                    "relations:reverb");
        }
        return extracted;
    }

    private List<int[]> addTokenAnnotations(ChunkedSentence sent, int sid, View view) {
        int tid = 1;
        List<int[]> tokenSpans = new ArrayList<>();
        for (String token : sent.getTokens()) {
            Range curTokSpan = sent.getOffsets().get(tid - 1);
            int start = curTokSpan.getStart(); int end = curTokSpan.getEnd();
            tokenSpans.add(new int[]{start, end});
            Annotation tok = view.newAnnotation(
                    makeID("tk_", sid, tid++), Uri.TOKEN,
                    start, end);
            tok.addFeature("word", token);
        }
        return tokenSpans;
    }

    /**
     * Load metadata from compiler generated json files.
     * @throws IOException when metadata json file was not found.
     */
    public void loadMetadata() throws IOException {
        // get caller name using reflection
        String serviceName = this.getClass().getName();
        String resUri = String.format("/metadata/%s.json", serviceName);
        log.info("load resources:" + resUri);
        InputStream inputStream = this.getClass().getResourceAsStream(resUri);

        if (inputStream == null) {
            String message = "Unable to load metadata file for " + serviceName;
            log.error(message);
            throw new IOException(message);
        } else {
            UTF8Reader reader = new UTF8Reader(inputStream);
            try {
                Scanner s = new Scanner(reader).useDelimiter("\\A");
                String metadataText = s.hasNext() ? s.next() : "";
                // somehow, org.lappsgrid.annotations module cannot properly get version
                // from pom.xml before compilation, so I add it manually in runtime
                ServiceMetadata parsedMetadata = Serializer.parse(metadataText, ServiceMetadata.class);
                parsedMetadata.setVersion(getVersion());
                metadata = (new Data<>(Uri.META, parsedMetadata)).asPrettyJson();
            } catch (Exception e) {
                String message = "Unable to parse json for " + this.getClass().getName();
                log.error(message, e);
                metadata = (new Data<>(Uri.ERROR, message)).asPrettyJson();
            }
            reader.close();
        }
    }

    @Override
    public String getMetadata() {
        return this.metadata;
    }

    /* ================= some helpers ================== */
    /**
     * Generates ID string
     */
    protected static String makeID(String type, int sid, int tid) {
        return String.format("%s%d_%d", type, sid, tid);
    }

    /**
     * Generates ID string
     */
    protected static String makeID(String type, int id) {
        return String.format("%s%d", type, id);

    }

}

