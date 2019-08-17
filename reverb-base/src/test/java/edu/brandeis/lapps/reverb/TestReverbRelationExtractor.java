package edu.brandeis.lapps.reverb;

import edu.brandeis.lapps.TestBrandeisService;
import org.junit.Test;
import org.lappsgrid.metadata.IOSpecification;
import org.lappsgrid.metadata.ServiceMetadata;
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
public class TestReverbRelationExtractor extends TestBrandeisService {

    public TestReverbRelationExtractor() {
        service = new ReverbRelationExtractor();
    }

    @Test
    public void testMetadata(){
        ServiceMetadata metadata = super.testDefaultMetadata();

        IOSpecification requires = metadata.getRequires();
        assertEquals("Requires encoding is not correct", "UTF-8", requires.getEncoding());
        List<String> list = requires.getFormat();
        assertTrue("LIF not accepted.", list.contains(Uri.LIF));
        assertTrue("Text not accepted", list.contains(Uri.TEXT));
        list = requires.getAnnotations();
        assertEquals("Required annotations should be empty", 0, list.size());

        IOSpecification produces = metadata.getProduces();
        assertEquals("Too many output formats", 1, produces.getFormat().size());
        assertEquals("Produces encoding is not correct", "UTF-8", produces.getEncoding());
        assertEquals("LIF not produced", Uri.LIF, produces.getFormat().get(0));
        assertEquals("Expected 1 annotation, found: " + produces.getAnnotations().size(),
                3, produces.getAnnotations().size());
        assertTrue("Tokens not produced", produces.getAnnotations().contains(Uri.TOKEN));
        assertTrue("Markables not produced", produces.getAnnotations().contains(Uri.MARKABLE));
        assertTrue("Relations not produced", produces.getAnnotations().contains(Uri.GENERIC_RELATION));
    }

    @Test
    public void testExecute() {
        String testSents = "Mary loves John, but John hates her. Sally is his wife. ";
        Container executionResult = super.testExecuteFromPlainAndLIFWrapped(testSents);

        List<View> views = executionResult.getViews();
        if (views.size() != 1) {
            fail(String.format("Expected 1 view. Found: %d", views.size()));
        }
        View view = executionResult.getView(0);
        assertTrue("View not containing Tokens", view.contains(Uri.TOKEN));
        assertTrue("View not containing Markables", view.contains(Uri.MARKABLE));
        assertTrue("View not containing Relations", view.contains(Uri.GENERIC_RELATION));
        Collection<Annotation> relations = getRelations(view);
        assertEquals("Expected 3 relations extracted, found: " + relations.size(),
                3, relations.size());
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
}