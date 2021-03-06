/*******************************************************************************
 * Copyright 2012 University of Southern California
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * 	http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * This code was developed by the Information Integration Group as part 
 * of the Karma project at the Information Sciences Institute of the 
 * University of Southern California.  For more information, publications, 
 * and related projects, please see: http://www.isi.edu/integration
 ******************************************************************************/

package edu.isi.karma.modeling.alignment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.jgrapht.graph.DirectedWeightedMultigraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.google.common.math.BigIntegerMath;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import edu.isi.karma.rep.alignment.ColumnNode;
import edu.isi.karma.rep.alignment.DefaultLink;
import edu.isi.karma.rep.alignment.InternalNode;
import edu.isi.karma.rep.alignment.LabeledLink;
import edu.isi.karma.rep.alignment.Node;
import edu.isi.karma.rep.alignment.SemanticType;
import edu.isi.karma.rep.alignment.SemanticType.Origin;

public class SemanticModel {

	private static Logger logger = LoggerFactory.getLogger(SemanticModel.class);

	protected String id;
	protected String name;
	protected String description;
	protected DirectedWeightedMultigraph<Node, LabeledLink> graph;
	protected List<ColumnNode> sourceColumns;
	protected Map<ColumnNode, ColumnNode> mappingToSourceColumns;
	protected final static int maxPathLengthForEvaluation = 2;
	
	public SemanticModel(
			String id,
			DirectedWeightedMultigraph<Node, LabeledLink> graph) {
		this.id = id;
		this.graph = graph;
		this.sourceColumns = this.getColumnNodes();
		this.mappingToSourceColumns = new HashMap<ColumnNode, ColumnNode>();
		for (ColumnNode c : this.sourceColumns)
			this.mappingToSourceColumns.put(c, c);
		this.setUserSelectedTypeForColumnNodes();
	}
	
	public SemanticModel(
			String id,
			DirectedWeightedMultigraph<Node, LabeledLink> graph,
			List<ColumnNode> sourceColumns,
			Map<ColumnNode, ColumnNode> mappingToSourceColumns) {
		this.id = id;
		this.graph = graph;
		this.sourceColumns = sourceColumns;
		this.mappingToSourceColumns = mappingToSourceColumns;
	}
	
	public SemanticModel(SemanticModel semanticModel) {
		this.id = semanticModel.getId();
		this.name = semanticModel.getName();
		this.description = semanticModel.getDescription();
		this.graph = semanticModel.getGraph();
		this.sourceColumns = semanticModel.getSourceColumns();
		this.mappingToSourceColumns = semanticModel.getMappingToSourceColumns();
	}
	
	public String getId() {
		return this.id;
	}
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public DirectedWeightedMultigraph<Node, LabeledLink> getGraph() {
		return graph;
	}

	public Map<ColumnNode, ColumnNode> getMappingToSourceColumns() {
		return mappingToSourceColumns;
	}
	
	public List<ColumnNode> getSourceColumns() {
		return sourceColumns;
	}
	
	public Set<InternalNode> getInternalNodes() {
		Set<InternalNode> internalNodes = new HashSet<InternalNode>();
		if (this.graph != null) {
			for (Node n : this.graph.vertexSet())
				if (n instanceof InternalNode)
					internalNodes.add((InternalNode)n);
		}
		return internalNodes;
	}

	public List<ColumnNode> getColumnNodes() {
		List<ColumnNode> columnNodes = new LinkedList<ColumnNode>();
		if (this.graph != null) {
			for (Node n : this.graph.vertexSet())
				if (n instanceof ColumnNode)
					columnNodes.add((ColumnNode)n);
		}
		return columnNodes;
	}
	
	private void setUserSelectedTypeForColumnNodes() {
		
		if (this.graph == null || this.sourceColumns == null)
			return;
		
		for (Node n : this.graph.vertexSet()) {
			
			if (!(n instanceof ColumnNode)) continue;
			
			ColumnNode cn = (ColumnNode)n;
			
			if (!this.sourceColumns.contains(cn)) continue;
			
			Set<LabeledLink> incomingLinks = this.graph.incomingEdgesOf(n);
			if (incomingLinks != null && incomingLinks.size() == 1) {
				LabeledLink link = incomingLinks.toArray(new LabeledLink[0])[0];
				Node domain = link.getSource();
				SemanticType st = new SemanticType(cn.getHNodeId(), link.getLabel(), domain.getLabel(), Origin.User, 1.0, false);
				cn.setUserSelectedSemanticType(st);
			} else
				logger.debug("The column node " + ((ColumnNode)n).getColumnName() + " does not have any domain or it has more than one domain.");
		}
	}
	
	public void print() {
		logger.info("id: " + this.getId());
		logger.info("name: " + this.getName());
		logger.info("description: " + this.getDescription());
		logger.info(GraphUtil.labeledGraphToString(this.graph));
	}

	public ModelEvaluation evaluate(SemanticModel baseModel) {

		if (baseModel == null || baseModel.getGraph() == null || this.getGraph() == null)
			return new ModelEvaluation(null, null, null);
		
		NodeIdFactory nodeIdFactory = new NodeIdFactory();
		HashMap<Node,String> baseNodeIds = new HashMap<Node,String>();
		for (Node n : baseModel.getGraph().vertexSet()) {
			if (n instanceof InternalNode)
				baseNodeIds.put(n, nodeIdFactory.getNodeId(n.getUri()));
			else // n is a column node
				baseNodeIds.put(n, n.getId());
		}
		
		Set<String> baseTriples = getTriples(baseModel.getGraph(), baseNodeIds);
		Set<String> targetTriples = null;
		List<HashMap<Node,String>> targetNodeIdSets = getPossibleNodeIdSets();
		if (targetNodeIdSets == null)
			return null;
		
		double bestFMeasure = 0.0;
		double bestPrecision = 0.0, bestRecall = 0.0;
		double precision, recall, fmeasure;
		for (HashMap<Node,String> targetNodeIds : targetNodeIdSets) {
//			System.out.println("==============================");
			targetTriples = getTriples(this.getGraph(), targetNodeIds);
			precision = getPrecision(baseTriples, targetTriples);
			recall = getRecall(baseTriples, targetTriples);
			fmeasure = 2 * precision * recall / (precision + recall);
			if (fmeasure > bestFMeasure) {
				bestFMeasure = fmeasure;
				bestPrecision = precision;
				bestRecall = recall;
			}
		}
		
		return new ModelEvaluation(0.0, bestPrecision, bestRecall);
	}
	
	private Set<String> getTriples(DirectedWeightedMultigraph<Node, LabeledLink> g, HashMap<Node,String> nodeIds) {
		
		String separator = "|";
		Set<String> triples = new HashSet<String>();
		if (g == null)
			return triples;
		
		String s, p, o, triple;
		for (LabeledLink l : g.edgeSet()) {
			
			s = nodeIds.get(l.getSource());
			o = nodeIds.get(l.getTarget());
			p = l.getLabel().getUri();
			triple = s + separator + p + separator + o;
//			System.out.println(triple);
			triples.add(triple);
		}
		
		return triples;
	}
	
	private List<HashMap<Node,String>> getPossibleNodeIdSets() {
		
		DirectedWeightedMultigraph<Node, LabeledLink> g = this.getGraph();
		List<HashMap<Node,String>> nodeIdSets = new ArrayList<HashMap<Node,String>>();
		if (g == null)
			return nodeIdSets;
		
		Set<Node> internalNodes = new HashSet<Node>();
		for (Node n : g.vertexSet()) 
			if (n instanceof InternalNode) 
				internalNodes.add(n);

		Set<Node> columnNodes = new HashSet<Node>();
		for (Node n : g.vertexSet()) 
			if (n instanceof ColumnNode) 
				columnNodes.add(n);

		Function<Node, String> sameUriNodes = new Function<Node, String>() {
			  @Override public String apply(final Node n) {
				  if (n == null || n.getLabel() == null)
					  return null;
				  return n.getLabel().getUri();
			  }
			};

		Multimap<String, Node> index = Multimaps.index(internalNodes, sameUriNodes);	
		int numberOfPossibleSets = 1;
		for (String s : index.keySet()) {
			Collection<Node> nodeGroup = index.get(s);
			if (nodeGroup != null && !nodeGroup.isEmpty()) {
				numberOfPossibleSets *= BigIntegerMath.factorial(nodeGroup.size()).intValue();
			}
		}
//		System.out.println(numberOfPossibleSets);

		for (int i = 0; i < numberOfPossibleSets; i++) {
			HashMap<Node, String> nodeIds = new HashMap<Node,String>();
			NodeIdFactory nodeIdFactory = new NodeIdFactory();
			
			for (Node n : columnNodes) {
				ColumnNode cn = null;
				if (this.mappingToSourceColumns != null && 
						(cn = this.mappingToSourceColumns.get((ColumnNode)n)) != null) {
					nodeIds.put(n, cn.getId());
				} else {
					nodeIds.put(n, n.getId());
				}
			}
			
			for (String s : index.keySet()) {
				Collection<Node> nodeGroup = index.get(s);
				if (nodeGroup != null && nodeGroup.size() == 1) {
					Node n = nodeGroup.iterator().next();
					nodeIds.put(n, nodeIdFactory.getNodeId(n.getLabel().getUri()));
				}
			}
	
			nodeIdSets.add(nodeIds);
		}
		
//		List<Node> nodes = new ArrayList<Node>();
//		List<Set<String>> idList = new ArrayList<Set<String>>(); 
//		Set<List<String>> idProductSets = null;
//		NodeIdFactory nodeIdFactory = new NodeIdFactory();
//		for (String s : index.keySet()) {
//			Collection<Node> nodeGroup = index.get(s);
//			if (nodeGroup == null)
//				continue;
//			nodes.addAll(nodeGroup);
//			Set<String> ids = new TreeSet<String>();
//			for (Node n : nodeGroup) {
//				ids.add(nodeIdFactory.getNodeId(n.getLabel().getUri()));
//			}
//			idList.add(ids);
//			
//		}
//		if (idList != null)
//			idProductSets = Sets.cartesianProduct(idList);
//		
//		int i = 0;
//		for (List<String> ids : idProductSets) {
//			for (Node n : nodes) System.out.println("node: " + n.getLabel().getUri());
//			for (String id : ids) System.out.println("id: " + id);
//
//			for (int j = 0; j < ids.size() && j < nodes.size(); j++)
//				nodeIdSets.get(i).put(nodes.get(j), ids.get(j));
//			i++;
//		}
		
		int interval = numberOfPossibleSets;
		NodeIdFactory nodeIdFactory = new NodeIdFactory();
		for (String s : index.keySet()) {
			Collection<Node> nodeGroup = index.get(s);
			if (nodeGroup != null && nodeGroup.size() > 1) {
				Set<String> ids = new HashSet<String>();
				List<Node> nodes = new ArrayList<Node>();
				nodes.addAll(nodeGroup);
				for (Node n : nodes)
					ids.add(nodeIdFactory.getNodeId(n.getLabel().getUri()));

				Collection<List<String>> permutations = Collections2.permutations(ids);
				List<List<String>> permList = new ArrayList<List<String>>();
				permList.addAll(permutations);
				
				interval = interval / nodeGroup.size();
				List<String> perm;
				int k = 0, count = 1;
				for (int i = 0; i < nodeIdSets.size(); i++) {
					HashMap<Node, String> nodeIds = nodeIdSets.get(i);
					if (count > interval) { k = ++k % permList.size(); count = 1;}
					perm = permList.get(k);
					for (int j = 0; j < nodes.size(); j++)
						nodeIds.put(nodes.get(j), perm.get(j));
					count ++;
				}
			}
		}

		
		return nodeIdSets;
	}
	
	public ModelEvaluation evaluate_old(SemanticModel baseModel) {

		if (baseModel == null || baseModel.getGraph() == null || this.getGraph() == null)
			return new ModelEvaluation(null, null, null);
		
		Double distance = getDistance(baseModel);
		
		int maxLength = maxPathLengthForEvaluation;
		List<GraphPath> basePaths = new LinkedList<GraphPath>();
		List<GraphPath> modelPaths = new LinkedList<GraphPath>();
		List<GraphPath> gpList;
		String pathStr;
		for (int i = 1; i <= maxLength; i++) {
			gpList = GraphUtil.getPaths(GraphUtil.asDefaultGraph(baseModel.graph), i);
			if (gpList != null) basePaths.addAll(gpList);
			gpList = GraphUtil.getPaths(GraphUtil.asDefaultGraph(this.graph), i);
			if (gpList != null) modelPaths.addAll(gpList);
		}
		Set<String> basePathString = new HashSet<String>();
		Set<String> modelPathString = new HashSet<String>();
		
		Map<String, Integer> visitedPaths = new HashMap<String, Integer>();
		Integer count;
		for (GraphPath gp : basePaths) {
			pathStr = getPathString(baseModel, gp);
			count = visitedPaths.get(pathStr);
			if (count == null) count = 1; else count++;
			visitedPaths.put(pathStr, count);
			if (pathStr != null && !pathStr.isEmpty()) 
				basePathString.add("(" + count + ")" + pathStr);
		}
		visitedPaths.clear();
		for (GraphPath gp : modelPaths) {
			pathStr = getPathString(this, gp);
			count = visitedPaths.get(pathStr);
			if (count == null) count = 1; else count++;
			visitedPaths.put(pathStr, count);
			if (pathStr != null && !pathStr.isEmpty()) 
				modelPathString.add("(" + count + ")" + pathStr);
		}

		Double precision = getPrecision(basePathString, modelPathString);
		Double recall = getRecall(basePathString, modelPathString);
		
		return new ModelEvaluation(distance, precision, recall);
	}
	
	private String getPathString(SemanticModel sm, GraphPath gp) {
		
		String str = "";
		String separator = "|";
		
		if (gp == null || gp.getLength() == 0)
			return null;
		Node target;
		
		if (gp.getStartNode() == null || gp.getStartNode().getLabel() == null)
			return null;
		
		str += gp.getStartNode().getLabel().getUri();
		str += separator;
		
		for (DefaultLink l : gp.getLinks()) {
			
			target = l.getTarget();
			if (target == null)
				return null;
			
			str += l.getUri();
			str += separator;
					
			if (target instanceof InternalNode) {
				if (target.getLabel() == null)
					return null;
				str += l.getUri();
				str += separator;
			}
			
			if (target instanceof ColumnNode) {
				ColumnNode cn = sm.mappingToSourceColumns.get((ColumnNode)target);
				if (cn == null) return null;
				str += cn.getId();
				str += separator;
			}
			
		}
		return str;
	}
	
	private Double getDistance(SemanticModel sm) {
		
		if (this.graph == null || sm.graph == null)
			return null;
		
		if (this.mappingToSourceColumns == null || sm.mappingToSourceColumns == null)
			return null;

		SemanticModel mainModel = this;
		SemanticModel targetModel = sm;
		
		int nodeInsertion = 0, 
				nodeDeletion = 0, 
				linkInsertion = 0, 
				linkDeletion = 0,
				linkRelabeling = 0;
		
		HashMap<String, Integer> mainNodes = new HashMap<String, Integer>();
		HashMap<String, Integer> targetNodes = new HashMap<String, Integer>();

		HashMap<String, Integer> mainLinks = new HashMap<String, Integer>();
		HashMap<String, Integer> targetLinks = new HashMap<String, Integer>();
		
		HashMap<String, Set<String>> mainNodePairToLinks = new HashMap<String, Set<String>>();
		HashMap<String, Set<String>> targetNodePairToLinks = new HashMap<String, Set<String>>();

		String key, sourceStr, targetStr, linkStr;
		Integer count = 0;
		
		// Adding the nodes to the maps
		for (Node n : mainModel.graph.vertexSet()) {
			if (n instanceof InternalNode) key = n.getLabel().getUri();
			else if (n instanceof ColumnNode) {
				ColumnNode cn = mainModel.mappingToSourceColumns.get(n);
				if (cn == null) continue; else key = cn.getId();
			}
			else continue;
			
			count = mainNodes.get(key);
			if (count == null) mainNodes.put(key, 1);
			else mainNodes.put(key, ++count);
		}
		for (Node n : targetModel.graph.vertexSet()) {
			if (n instanceof InternalNode) key = n.getLabel().getUri();
			else if (n instanceof ColumnNode) {
				ColumnNode cn = targetModel.mappingToSourceColumns.get(n);
				if (cn == null) continue; else key = cn.getId();
			}
			else continue;
			
			count = targetNodes.get(key);
			if (count == null) targetNodes.put(key, 1);
			else targetNodes.put(key, ++count);
		}
		
		// Adding the links to the maps
		Node source, target;
		for (LabeledLink l : mainModel.graph.edgeSet()) {			
			source = l.getSource();
			target = l.getTarget();
			
			if (!(source instanceof InternalNode)) continue;
			
			sourceStr = source.getLabel().getUri();
			linkStr = l.getLabel().getUri();
			if (target instanceof InternalNode) targetStr = target.getLabel().getUri();
			else if (target instanceof ColumnNode) {
				ColumnNode cn = mainModel.mappingToSourceColumns.get(target);
				if (cn == null) continue; else targetStr = cn.getId();
			}
			else continue;
			
			key = sourceStr + linkStr + targetStr;
			count = mainLinks.get(key);
			if (count == null) mainLinks.put(key, 1);
			else mainLinks.put(key, ++count);
			
			Set<String> links = mainNodePairToLinks.get(sourceStr + targetStr);
			if (links == null) { links = new HashSet<String>(); mainNodePairToLinks.put(sourceStr + targetStr, links); }
			links.add(linkStr);
		}
		for (LabeledLink l : targetModel.graph.edgeSet()) {
			source = l.getSource();
			target = l.getTarget();
			
			if (!(source instanceof InternalNode)) continue;
			
			sourceStr = source.getLabel().getUri();
			linkStr = l.getLabel().getUri();
			if (target instanceof InternalNode) targetStr = target.getLabel().getUri();
			else if (target instanceof ColumnNode) {
				ColumnNode cn = targetModel.mappingToSourceColumns.get(target);
				if (cn == null) continue; else targetStr = cn.getId();
			}
			else continue;
			
			key = sourceStr + linkStr + targetStr;
			count = targetLinks.get(key);
			if (count == null) targetLinks.put(key, 1);
			else targetLinks.put(key, ++count);
			
			Set<String> links = targetNodePairToLinks.get(sourceStr + targetStr);
			if (links == null) { links = new HashSet<String>(); targetNodePairToLinks.put(sourceStr + targetStr, links); }
			links.add(linkStr);
		}
		
		int diff;
		for (Entry<String, Integer> mainNodeEntry : mainNodes.entrySet()) {
			count = targetNodes.get(mainNodeEntry.getKey());
			if (count == null) count = 0;
			diff = mainNodeEntry.getValue() - count;
			nodeInsertion += diff > 0? diff : 0;
		}
		
		for (Entry<String, Integer> targetNodeEntry : targetNodes.entrySet()) {
			count = mainNodes.get(targetNodeEntry.getKey());
			if (count == null) count = 0;
			diff = targetNodeEntry.getValue() - count;
			nodeDeletion += diff > 0? diff : 0;
		}

		for (Entry<String, Integer> mainLinkEntry : mainLinks.entrySet()) {
			count = targetLinks.get(mainLinkEntry.getKey());
			if (count == null) count = 0;
			diff = mainLinkEntry.getValue() - count;
			linkInsertion += diff > 0? diff : 0;
		}
		
		for (Entry<String, Integer> targetLinkEntry : targetLinks.entrySet()) {
			count = mainLinks.get(targetLinkEntry.getKey());
			if (count == null) count = 0;
			diff = targetLinkEntry.getValue() - count;
			linkDeletion += diff > 0? diff : 0;
		}

		for (Entry<String, Set<String>> mainNodePairToLinksEntry : mainNodePairToLinks.entrySet()) {
			if (!targetNodePairToLinks.containsKey(mainNodePairToLinksEntry.getKey()))
				continue;
			Set<String> mainRelations = mainNodePairToLinksEntry.getValue();
			Set<String> targetRelations = targetNodePairToLinks.get(mainNodePairToLinksEntry.getKey());
			linkRelabeling += targetRelations.size() > mainRelations.size() ? 
					mainRelations.size() - Sets.intersection(mainRelations, targetRelations).size() :
					targetRelations.size() - Sets.intersection(mainRelations, targetRelations).size();
		}
		
		linkInsertion -= linkRelabeling;
		linkDeletion -= linkRelabeling;

		logger.debug("node insertion cost: " + nodeInsertion);
		logger.debug("node deletion cost: " + nodeDeletion);
		logger.debug("link insertion cost: " + linkInsertion);
		logger.debug("link deletion cost: " + linkDeletion);
		logger.debug("link relabeling cost: " + linkRelabeling);

		return (double)(nodeInsertion + nodeDeletion + linkInsertion + linkDeletion + linkRelabeling);
	}
	
	private Double getPrecision(Set<String> correct, Set<String> result) {
		
		if (correct == null || result == null)
			return null;
		
		int intersection = Sets.intersection(correct, result).size();
		int resultSize = result.size();
		
		return resultSize == 0 ? 0 : (double) intersection / (double) resultSize;
	}
	
	private Double getRecall(Set<String> correct, Set<String> result) {
		
		if (correct == null || result == null)
			return null;
		
		int intersection = Sets.intersection(correct, result).size();
		int correctSize = correct.size();
		
		return correctSize == 0 ? 0 : (double) intersection / (double) correctSize;	
	}
	
	public void writeGraphviz(String filename, boolean showNodeMetaData, boolean showLinkMetaData) throws IOException {
		GraphVizUtil.exportSemanticModelToGraphviz(this, showNodeMetaData, showLinkMetaData, filename);
	}
	
	public void writeJson(String filename) throws IOException {
		
		File file = new File(filename);
		if (!file.exists()) {
			file.createNewFile();
		}
		
		FileOutputStream out = new FileOutputStream(file); 
		JsonWriter writer = new JsonWriter(new OutputStreamWriter(out, "UTF-8"));
		writer.setIndent("    ");
		try {
			writeModel(writer);
		} catch (Exception e) {
			logger.error("error in writing the model in json!");
	    	e.printStackTrace();
	     } finally {
			writer.close();
		}
		
	}
	
	private void writeModel(JsonWriter writer) throws IOException {
		String nullStr = null;
		writer.beginObject();
		writer.name("id").value(this.getId());
		writer.name("name").value(this.getName());
		writer.name("description").value(this.getDescription());
		writer.name("sourceColumns");
		if (this.sourceColumns == null) writer.value(nullStr);
		else {
			writer.beginArray();
			for (ColumnNode cn : this.sourceColumns) {
				writeSourceColumn(writer, cn);
			}
			writer.endArray();
		}
		writer.name("mappingToSourceColumns");
		if (this.mappingToSourceColumns == null) writer.value(nullStr);
		else {
			writer.beginArray();
			for (Entry<ColumnNode, ColumnNode> mapping : this.mappingToSourceColumns.entrySet()) {
				writeMappingToSourceColumn(writer, mapping);
			}
			writer.endArray();
		}
		writer.name("graph");
		if (this.graph == null) writer.value(nullStr);
		else GraphUtil.writeGraph(GraphUtil.asDefaultGraph(this.graph), writer);
		writer.endObject();
	}

	private void writeSourceColumn(JsonWriter writer, ColumnNode sourceColumn) throws IOException {
		
		if (sourceColumn == null)
			return;
		
		writer.beginObject();
		writer.name("id").value(sourceColumn.getId());
		writer.name("hNodeId").value(sourceColumn.getHNodeId());
		writer.name("columnName").value(sourceColumn.getColumnName());
		writer.endObject();
	}

	private void writeMappingToSourceColumn(JsonWriter writer, Entry<ColumnNode, ColumnNode> mapping) throws IOException {
		
		if (mapping == null || mapping.getKey() == null || mapping.getValue() == null)
			return;
		
		writer.beginObject();
		writer.name("id").value(mapping.getKey().getId());
		writer.name("sourceColumnId").value(mapping.getValue().getId());
		writer.endObject();
	}
	
	public static SemanticModel readJson(String filename) throws IOException {

		File file = new File(filename);
		if (!file.exists()) {
			logger.error("cannot open the file " + filename);
		}
		
		FileInputStream in = new FileInputStream(file);
		JsonReader reader = new JsonReader(new InputStreamReader(in, "UTF-8"));
	    try {
	    	return readModel(reader);
	    } catch (Exception e) {
	    	logger.error("error in reading the model from json!");
	    	e.printStackTrace();
	    	return null;
	    } finally {
	    	reader.close();
	    }
	}
	
	private static SemanticModel readModel(JsonReader reader) throws IOException {
		
		String id = null;
		String name = null;
		String description = null;
		DirectedWeightedMultigraph<Node, DefaultLink> graph = null;
		List<ColumnNode> sourceColumns = null;
		HashMap<String, ColumnNode> sourceColumnIds = null;

		Map<String, String> mappingToSourceColumnsIds = null;
		
		reader.beginObject();
	    while (reader.hasNext()) {
	    	String key = reader.nextName();
			if (key.equals("id") && reader.peek() != JsonToken.NULL) {
				id = reader.nextString();
			} else if (key.equals("name") && reader.peek() != JsonToken.NULL) {
				name = reader.nextString();
			} else if (key.equals("description") && reader.peek() != JsonToken.NULL) {
				description = reader.nextString();
			} else if (key.equals("sourceColumns") && reader.peek() != JsonToken.NULL) {
				sourceColumns = new LinkedList<ColumnNode>();
				sourceColumnIds = new HashMap<String, ColumnNode>();
				reader.beginArray();
			    while (reader.hasNext()) {
			    	ColumnNode cn = readSourceColumn(reader);
			    	if (cn != null) {
			    		sourceColumns.add(cn);
			    		sourceColumnIds.put(cn.getId(), cn);
			    	}
				}
		    	reader.endArray();				
			} else if (key.equals("mappingToSourceColumns") && reader.peek() != JsonToken.NULL) {
				mappingToSourceColumnsIds = new HashMap<String, String>();
				Map <String, String> oneEntryMapping;
				reader.beginArray();
			    while (reader.hasNext()) {
			    	oneEntryMapping = readMappingToSourceColumn(reader);
			    	if (oneEntryMapping != null && oneEntryMapping.size() == 1)
			    		mappingToSourceColumnsIds.putAll(oneEntryMapping);
				}
		    	reader.endArray();				
			} else if (key.equals("graph") && reader.peek() != JsonToken.NULL) {
				graph = GraphUtil.readGraph(reader);
			} else {
				reader.skipValue();
			}
		}
    	reader.endObject();
    	
		Map<ColumnNode, ColumnNode> mappingToSourceColumns = new HashMap<ColumnNode, ColumnNode>();
		if (graph != null && mappingToSourceColumnsIds != null && !mappingToSourceColumnsIds.isEmpty()) {
			for (Node n : graph.vertexSet()) {
				if (n instanceof ColumnNode) {
					ColumnNode cn = (ColumnNode)n;
					String sourceColumnId = mappingToSourceColumnsIds.get(cn.getId());
					if (sourceColumnId != null) {
						ColumnNode sourceColumn = sourceColumnIds.get(sourceColumnId);
						if (sourceColumn != null)
							mappingToSourceColumns.put(cn, sourceColumn);
					}
				}
			}
		}

    	SemanticModel semanticModel = new SemanticModel(id, GraphUtil.asLabeledGraph(graph), sourceColumns, mappingToSourceColumns);
    	semanticModel.setName(name);
    	semanticModel.setDescription(description);
    	
    	return semanticModel;
	}
	
	private static ColumnNode readSourceColumn(JsonReader reader) throws IOException {
		String id = null;
		String hNodeId = null;
		String columnName = null;
		
		reader.beginObject();
	    while (reader.hasNext()) {
	    	String key = reader.nextName();
			if (key.equals("id") && reader.peek() != JsonToken.NULL) {
				id = reader.nextString();
			} else if (key.equals("hNodeId") && reader.peek() != JsonToken.NULL) {
				hNodeId = reader.nextString();
			} else if (key.equals("columnName") && reader.peek() != JsonToken.NULL) {
				columnName = reader.nextString();
			} else {
			  reader.skipValue();
			}
		}
    	reader.endObject();
    	
    	ColumnNode cn = new ColumnNode(id, hNodeId, columnName, null);
    	return cn;
	}

	private static Map<String, String> readMappingToSourceColumn(JsonReader reader) throws IOException {
		String id = null;
		String sourceColumnId = null;
		
		reader.beginObject();
	    while (reader.hasNext()) {
	    	String key = reader.nextName();
			if (key.equals("id") && reader.peek() != JsonToken.NULL) {
				id = reader.nextString();
			} else if (key.equals("sourceColumnId") && reader.peek() != JsonToken.NULL) {
				sourceColumnId = reader.nextString();
			} else {
			  reader.skipValue();
			}
		}
    	reader.endObject();
    	
    	Map<String, String> mapping = new HashMap<String, String>();
    	mapping.put(id, sourceColumnId);
    	return mapping;
	}
}
