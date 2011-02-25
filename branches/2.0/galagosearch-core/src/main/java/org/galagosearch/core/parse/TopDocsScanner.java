/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.galagosearch.core.parse;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.logging.Logger;
import org.galagosearch.core.index.DocumentLengthsReader;
import org.galagosearch.core.index.KeyListReader;
import org.galagosearch.core.index.PositionIndexReader;
import org.galagosearch.core.index.StructuredIndex;
import org.galagosearch.core.retrieval.structured.NumberedDocumentDataIterator;
import org.galagosearch.core.types.KeyValuePair;
import org.galagosearch.core.types.NumberedDocumentData;
import org.galagosearch.core.types.TopDocsEntry;
import org.galagosearch.tupleflow.Counter;
import org.galagosearch.tupleflow.FileSource;
import org.galagosearch.tupleflow.InputClass;
import org.galagosearch.tupleflow.OutputClass;
import org.galagosearch.tupleflow.StandardStep;
import org.galagosearch.tupleflow.TupleFlowParameters;
import org.galagosearch.tupleflow.Utility;
import org.galagosearch.tupleflow.execution.ErrorHandler;

/**
 *
 * @author marc
 */
@InputClass(className="org.galagosearch.core.types.KeyValuePair", order={"+key"})
@OutputClass(className="org.galagosearch.core.types.TopDocsEntry", order={"+word", "+document"})
public class TopDocsScanner extends StandardStep<KeyValuePair, TopDocsEntry> {
    private Logger LOG = Logger.getLogger(getClass().toString());
  
    public static class NWPComparator implements Comparator<TopDocsEntry> {
        public int compare(TopDocsEntry a, TopDocsEntry b) {
            int result = (a.probability > b.probability) ? 1 :
                ((a.probability < b.probability) ? -1 : 0);
            if (result != 0) return result;
            return (a.document - b.document);
        }
    }

    protected int size;
    protected long minlength;
    protected long count;
    Counter counter;
    PriorityQueue<TopDocsEntry> topdocs;
    PositionIndexReader partReader;
    NumberedDocumentDataIterator docLengths;
    DocumentLengthsReader docReader;
    KeyListReader.ListIterator extentIterator;
    TopDocsEntry tde;

    public TopDocsScanner(TupleFlowParameters parameters) throws IOException {
        size = (int) parameters.getXML().get("size", Integer.MAX_VALUE);
        minlength = parameters.getXML().get("minlength", Long.MAX_VALUE);
        topdocs = new PriorityQueue<TopDocsEntry>(size, new NWPComparator());
        String indexLocation = parameters.getXML().get("directory");
        docReader = new DocumentLengthsReader(indexLocation +
                File.separator + "documentLengths");
        docLengths = docReader.getIterator();
        partReader = new PositionIndexReader(StructuredIndex.getPartPath(indexLocation, parameters.getXML().get("part")));
        counter = parameters.getCounter("lists scanned");
    }

    @Override
    public void process(KeyValuePair object) throws IOException {
        if (counter != null) counter.increment();
        // Get out posting list
        count = 0;
        topdocs.clear();
        extentIterator = partReader.getTermCounts(Utility.toString(object.key));
        if (extentIterator instanceof PositionIndexReader.Iterator) {
            count = ((PositionIndexReader.Iterator) extentIterator).totalEntries();
            if (count < minlength) return; //short-circuit out
        }

        count = 0; // need to reset b/c we're going to count anyhow.

        // And iterate
        docLengths.reset();
        while (!extentIterator.isDone()) {
            count++;
            docLengths.skipToKey(extentIterator.currentIdentifier());
            NumberedDocumentData ndd = docLengths.getDocumentData();
            assert (ndd.number == extentIterator.currentIdentifier());
            int length = ndd.textLength;
            double probability = (0.0+extentIterator.count())
                    / (0.0+length);
            tde = new TopDocsEntry();
            tde.document = extentIterator.currentIdentifier();
            tde.count = extentIterator.count();
            tde.doclength = length;
            tde.probability = probability;
            topdocs.add(tde);

            // Keep it trimmed
            if (topdocs.size() > size) {
                topdocs.poll();
            }
            extentIterator.nextEntry();
        }

        // skip if it's too small
        if (count < minlength) {
            topdocs.clear();
            return;
        }

        while (topdocs.size() > size) {
          topdocs.poll();
        }

        // Now emit based on our top docs (have to reverse first)
        ArrayList<TopDocsEntry> resort = new ArrayList<TopDocsEntry>(topdocs);
        Collections.sort(resort, new Comparator<TopDocsEntry>() {
           public int compare(TopDocsEntry a, TopDocsEntry b) {
              return (a.document - b.document);
          }
        });

        for (TopDocsEntry entry : resort) {
            entry.word = object.key;
            processor.process(entry);
        }
    }

    public static void verify(TupleFlowParameters parameters, ErrorHandler handler) {
        FileSource.verify(parameters, handler);
        if (!parameters.getXML().containsKey("size")) handler.addError("Need size.");
        if (!parameters.getXML().containsKey("minlength")) handler.addError("Need minlength");
        if (!parameters.getXML().containsKey("part")) handler.addError("Need index part");
    }

    public void close() throws IOException {
        docReader.close();
        partReader.close();
        processor.close();
    }
}