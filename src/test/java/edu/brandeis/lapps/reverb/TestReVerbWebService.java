package edu.brandeis.lapps.reverb;

import org.junit.Test;
import org.lappsgrid.metadata.IOSpecification;
import org.lappsgrid.metadata.ServiceMetadata;
import org.lappsgrid.serialization.Data;
import org.lappsgrid.serialization.Serializer;
import org.lappsgrid.serialization.lif.Annotation;
import org.lappsgrid.serialization.lif.Container;
import org.lappsgrid.serialization.lif.View;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.*;
import static org.lappsgrid.discriminator.Discriminators.Uri;

/**
 * Tests for ReVerb relation extractor wrapper
 * @author krim@brandeis.edu
 */
public class TestReVerbWebService extends TestService {

    String simpleTestSent = "She swam to Paris.";
    String testSents = "Mary loves John, but John hates her.";

    public TestReVerbWebService() throws Exception {
        service = new ReVerbWebService();
    }

    @Test
    public void testMetadata(){
        ServiceMetadata metadata = super.testCommonMetadata();
        IOSpecification produces = metadata.getProduces();
        IOSpecification requires = metadata.getRequires();
        assertEquals("Expected 1 annotation, found: " + produces.getAnnotations().size(),
                3, produces.getAnnotations().size());
        assertTrue("Tokens not produced", produces.getAnnotations().contains(Uri.TOKEN));
        assertTrue("Markables not produced", produces.getAnnotations().contains(Uri.MARKABLE));
        assertTrue("Relations not produced", produces.getAnnotations().contains(Uri.GENERIC_RELATION));
    }

    @Test
    public void canProcessPureText() {
        String resultFromPure = service.execute(simpleTestSent);
        String leds = new Data<>(Uri.LIF, wrapContainer(simpleTestSent)).asJson();
        String resultFromLEDS = service.execute(leds);
        assertEquals("Results from pure text and LEDS(TEXT) are different.",
                resultFromPure, resultFromLEDS);

    }

    @Test
    public void canProcessSimpleSent(){
        String result = service.execute(simpleTestSent);
        System.out.println("<------------------------------------------------------------------------------");
        System.out.println(result);
        System.out.println("------------------------------------------------------------------------------>");
        Container resultCont = reconstructPayload(result);
        assertEquals("Text is corrupted.", simpleTestSent, resultCont.getText());
        List<View> views = resultCont.getViews();
        if (views.size() != 1) {
            fail(String.format("Expected 1 view. Found: %d", views.size()));
        }
        View view = resultCont.getView(0);
        assertTrue("View not containing Tokens", view.contains(Uri.TOKEN));
        assertTrue("View not containing Markables", view.contains(Uri.MARKABLE));
        assertTrue("View not containing Relations", view.contains(Uri.GENERIC_RELATION));
        Collection<Annotation> relations = getRelations(view);
        assertEquals("Expected 1 relation extracted, found: " + relations.size(),
                1, relations.size());
        System.out.println(Serializer.toPrettyJson(resultCont));
    }


    @Test(expected = NullPointerException.class)
    public void canProcessEmptySent() {
        String result = service.execute("");
        System.out.println("<------------------------------------------------------------------------------");
        System.out.println(result);
        System.out.println("------------------------------------------------------------------------------>");
        Container resultCont = reconstructPayload(result);
        assertEquals("Expected 0 annotations, found: " + resultCont.getView(0).getAnnotations().size(),
                0, resultCont.getView(0).getAnnotations().size());
        System.out.println(Serializer.toPrettyJson(resultCont));
        // this will throw NPE
        resultCont.getView(0).getMetadata().get("contains");
    }

    private Collection<Annotation> getRelations(View view) {
        Collection<Annotation> relations = new ArrayList<>();
        for (Annotation annotation : view.getAnnotations()) {
            if (annotation.getAtType().equals(Uri.GENERIC_RELATION)) {
                relations.add(annotation);
            }
        }
        return relations;
    }

    @Test
    public void canProcessLongerSent() {
        String input = new Data<>(Uri.LIF, wrapContainer(testSents)).asJson();
        String result = service.execute(input);
        System.out.println("<------------------------------------------------------------------------------");
        System.out.println(result);
        System.out.println("------------------------------------------------------------------------------>");
        Container resultCont = reconstructPayload(result);
        View view = resultCont.getView(0);
        Collection<Annotation> relations = getRelations(view);
        assertEquals("Expected 2 relations extracted, found: " + relations.size(),
                2, relations.size());
        System.out.println(Serializer.toPrettyJson(resultCont));

    }
}