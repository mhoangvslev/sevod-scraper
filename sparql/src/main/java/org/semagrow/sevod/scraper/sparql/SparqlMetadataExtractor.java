package org.semagrow.sevod.scraper.sparql;

import org.eclipse.rdf4j.model.*;
import org.eclipse.rdf4j.model.impl.ValueFactoryImpl;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.XMLSchema;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.semagrow.sevod.commons.vocabulary.SEVOD;
import org.semagrow.sevod.commons.vocabulary.VOID;
import org.semagrow.sevod.scraper.sparql.metadata.ClassMetadata;
import org.semagrow.sevod.scraper.sparql.metadata.DatasetMetadata;
import org.semagrow.sevod.scraper.sparql.metadata.Metadata;
import org.semagrow.sevod.scraper.sparql.metadata.PredicateMetadata;

import java.util.List;
import java.util.Set;

/**
 * Created by antonis on 29/7/2016.
 */
public class SparqlMetadataExtractor {

    private String endpoint;
    private String[] graphs;
    private Set<String> knownPrefixes;
    private ValueFactory vf = ValueFactoryImpl.getInstance();

    public SparqlMetadataExtractor(String endpoint, String[] baseGraph, Set<String> knownPrefixes) {
        this.endpoint = endpoint;
        this.graphs = baseGraph;
        this.knownPrefixes = knownPrefixes;
    }

    public void writeMetadata(RDFWriter writer) throws RDFHandlerException {

        BNode rootDataset = vf.createBNode("DatasetRoot");

        writer.handleNamespace(VOID.PREFIX, VOID.NAMESPACE);
        writer.handleNamespace(SEVOD.PREFIX, SEVOD.NAMESPACE);
        writer.handleNamespace(XMLSchema.PREFIX, XMLSchema.NAMESPACE);

        writer.handleStatement(vf.createStatement(rootDataset, RDF.TYPE, VOID.DATASET));

        QueryEvaluator eval = new QueryEvaluator(endpoint);
        QueryTransformer qt = new QueryTransformer();

        int datasetId = 0;
        for (String graph: graphs) {
            datasetId++;

            Resource subdataset = vf.createBNode("Dataset"+datasetId);
            writer.handleStatement(vf.createStatement(subdataset, RDF.TYPE, VOID.DATASET));
            writer.handleStatement(vf.createStatement(subdataset, VOID.SUBSET, rootDataset));

            List<IRI> predicates = eval.iris(qt.from(Queries.predicates).setGraph(graph).toString(), Queries.predicate_var);

            for (IRI predicate: predicates) {
                Metadata metadata = new PredicateMetadata(predicate, graph, knownPrefixes);
                metadata.processEndpoint(endpoint);
                metadata.serializeMetadata(subdataset, writer);
            }

            List<IRI> classes = eval.iris(qt.from(Queries.classes).setGraph(graph).toString(), Queries.class_var);

            for (IRI clazz: classes) {
                Metadata metadata = new ClassMetadata(clazz, graph, knownPrefixes);
                metadata.processEndpoint(endpoint);
                metadata.serializeMetadata(subdataset, writer);
            }

            Metadata metadata = new DatasetMetadata(graph);
            metadata.processEndpoint(endpoint);
            metadata.serializeMetadata(subdataset, writer);
        }
    }
}
