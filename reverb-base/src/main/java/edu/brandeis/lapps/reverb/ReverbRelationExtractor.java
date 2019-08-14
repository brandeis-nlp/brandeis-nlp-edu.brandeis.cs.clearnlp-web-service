package edu.brandeis.lapps.reverb;


import edu.brandeis.lapps.BrandeisService;
import edu.washington.cs.knowitall.commonlib.Range;
import edu.washington.cs.knowitall.extractor.ReVerbExtractor;
import edu.washington.cs.knowitall.extractor.SentenceExtractor;
import edu.washington.cs.knowitall.nlp.ChunkedSentence;
import edu.washington.cs.knowitall.nlp.ChunkedSentenceReader;
import edu.washington.cs.knowitall.nlp.extraction.ChunkedBinaryExtraction;
import edu.washington.cs.knowitall.nlp.extraction.ChunkedExtraction;
import edu.washington.cs.knowitall.util.DefaultObjects;
import org.lappsgrid.discriminator.Discriminators;
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

public class ReverbRelationExtractor extends BrandeisService {

    private static final Logger log = LoggerFactory.getLogger(ReverbRelationExtractor.class);

    /**
     * Default constructor.
     * Only loads up metadata json file.
     * These json files are generated from @annotation of this class by mvn compilation
     */
    public ReverbRelationExtractor() {
        try {
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
    protected String processPayload(Container lif) {
//    private String execute(Container lif) throws Exception {

        String inputText = lif.getText();
        if (inputText.length() > 0) {
            log.info("Loading sentence splitter && chunker");
            String[] sentences = new String[0];
            try {
                sentences = DefaultObjects.getDefaultSentenceDetector().sentDetect(inputText);
            } catch (IOException e) {
                errorLEDS("Couldn't find OpenNLP models to proceed.");
            }
            log.info("Done!");

            log.info("Loading relation extractor");
            log.info("Done!");

            View view = lif.newView();

            String serviceName = this.getClass().getName();
            view.addContains(Uri.TOKEN, String.format("%s:%s", serviceName, getVersion()),
                    "tokenization:reverb");

            int sidx = 1;
            int soffset = 0;
            for (String sentence : sentences) {
                soffset = inputText.indexOf(sentence, soffset);
                // We did sent-split first, using the same library. So we believe this "sentence" string only contains one sentence.

                // Use this default constructor, and add no "filter" that removes, for example bracketed substring (BracketsRemover).
                SentenceExtractor extractor = null;
                try {
                    extractor = new SentenceExtractor();
                } catch (IOException e) {
                    errorLEDS("Couldn't find OpenNLP models to proceed.");
                }
//                extractor.addMapper(new BracketsRemover());
//                extractor.addMapper(new SentenceEndFilter());
//                extractor.addMapper(new SentenceStartFilter());
                // And purge new line characters. "extractor" would ignore substring after the first newline otherwise
                String linepurged = sentence.replace("\n", " ");
                ChunkedSentenceReader reader = null;
                try {
                    reader = new ChunkedSentenceReader(new StringReader(linepurged), extractor);
                } catch (IOException e) {
                    errorLEDS("Couldn't find OpenNLP models to proceed.");
                }

                Iterator<ChunkedSentence> chunkedSentenceIterator = reader.getSentences().iterator();
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
                    Annotation relMid = addMarkbleAnnotation(sidx, midx++, tokenSpans, rel, view);
                    Annotation arg1Mid = addMarkbleAnnotation(sidx, midx++, tokenSpans, extr.getArgument1(), view);
                    Annotation arg2Mid = addMarkbleAnnotation(sidx, midx++, tokenSpans, extr.getArgument2(), view);

                    // use remembered markable IDs to add relation annotation
                    Annotation relation = view.newAnnotation(makeID("rel_", sidx, ridx++), Uri.GENERIC_RELATION);
                    relation.addFeature(Features.GenericRelation.ARGUMENTS, Arrays.asList(arg1Mid.getId(), arg2Mid.getId()));
                    relation.addFeature(Features.GenericRelation.RELATION, relMid.getId());
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
    private Annotation addMarkbleAnnotation(int sidx, int midx, List<int[]> tokenSpans, ChunkedExtraction markable, View view) {
        String markableId = makeID("m_", sidx, midx);
        final int firstTokenIdx = markable.getRange().getStart();
        int lastTokenIdx = markable.getRange().getEnd() - 1;
        int charOffsetStart = tokenSpans.get(firstTokenIdx)[0];
        int charOffsetEnd = tokenSpans.get(lastTokenIdx)[1];
        return view.newAnnotation(markableId, Uri.MARKABLE, charOffsetStart, charOffsetEnd);
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

    @Override
    /**
     * Load metadata from compiler generated json files.
     * @throws IOException when metadata json file was not found.
     */
    public ServiceMetadata loadMetadata() {

        ServiceMetadata metadata = setDefaultMetadata();
        metadata.setLicense("http://reverb.cs.washington.edu/LICENSE.txt");
        metadata.setLicenseDesc("This service provides an interface to a Reverb Relation Extraction tool, which is developed at UW and is originally licensed under Reverb Software License. For more information, please visit `the official CoreNLP website <http://reverb.cs.washington.edu/LICENSE.txt>`_. ");
        metadata.setDescription("This service is a wrapper around ReVerb Relation Extractor " + getWrappeeVersion() + " that extracts OpenIE style triples from input texts.");
        metadata.setAllow(Discriminators.Uri.ALL);

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

        return metadata;
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

