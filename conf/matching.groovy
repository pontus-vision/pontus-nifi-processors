import com.joestelmach.natty.DateGroup
import com.joestelmach.natty.Parser
import com.pontusvision.utils.LocationAddress
import com.pontusvision.utils.PostCode
import groovy.json.JsonSlurper
import groovy.text.GStringTemplateEngine
import groovy.text.Template
import org.apache.tinkerpop.gremlin.process.traversal.P
import org.codehaus.groovy.runtime.StringGroovyMethods

import java.util.concurrent.ConcurrentHashMap

/*
def benchmark = { closure ->
    start = System.nanoTime()
    closure.call()
    now = System.nanoTime()
    now - start
}
*/


class Convert<T> {
    private from
    private to


    public Convert(clazz) {
        from = clazz


    }

    static def from(clazz) {
        new Convert(clazz)
    }


    def to(clazz) {
        to = clazz
        return this
    }

    def using(closure) {
        def originalAsType = from.metaClass.getMetaMethod('asType', [] as Class[])
        from.metaClass.asType = { Class clazz ->
            if (clazz == to) {
                closure.setProperty('value', delegate)
                closure(delegate)
            } else {
                originalAsType.doMethodInvoke(delegate, clazz)
            }
        }
    }


    T fromString(String data, Class<T> requiredType, StringBuffer sb = null) {

        if (requiredType == Date.class) {
            return data as Date

        } else if (requiredType == String.class) {
            return data as T
        } else if (requiredType == Boolean.class) {
            return Boolean.valueOf(data) as T
        } else if (requiredType == Integer.class) {
            return Integer.valueOf(data) as T
        } else if (requiredType == Long.class) {
            return Long.valueOf(data) as T
        } else if (requiredType == Float.class) {
            return Float.valueOf(data) as T
        } else if (requiredType == Double.class) {
            return Double.valueOf(data) as T
        } else if (requiredType == Short.class) {
            return Short.valueOf(data) as T
        } else {
            return data as T
        }


    }
}

class PVConvMixin {


    static final def convert = StringGroovyMethods.&asType

    static Date invalidDate = new Date("01/01/1666")
    static Parser parser = new Parser();
    static {

        String.mixin(PVConvMixin)
    }

    static def asType(String self, Class cls, StringBuffer sb = null) {
        if (cls == Date) {

            List<DateGroup> dateGroup = parser.parse(self as String)
            Date retVal = null;

            if (!dateGroup.isEmpty()) {
                DateGroup dg = dateGroup.get(0)

                boolean isTimeInferred = dg.isTimeInferred();

                List<Date> dates = dg.getDates()

                sb?.append("\n\nConverting data $self; found $dates")
                dates.each {

                    retVal = it
                    if (isTimeInferred) {
                        Calendar calendar = Calendar.getInstance();
                        calendar.setTime(retVal);
                        calendar.set(Calendar.HOUR_OF_DAY, 1);
                        calendar.set(Calendar.MINUTE, 1);
                        calendar.set(Calendar.SECOND, 1);
                        calendar.set(Calendar.MILLISECOND, 1);
                        retVal = calendar.getTime();

                    }
                }
                if (retVal == null) {
                    sb?.append("\nCould not find a conversion for this date $self")

                    return null;
                }
            }
            return retVal;


        } else if (cls == PostCode) {
            return new PostCode(self)
        } else return convert(self, cls)
    }


}

PVConvMixin dummy = null
String.mixin(PVConvMixin)


def class PVValTemplate {
    private static GStringTemplateEngine engine = new GStringTemplateEngine(PVValTemplate.class.getClassLoader())

    private static Map<String, Template> templateMap = new ConcurrentHashMap<>();

    static Template getTemplate(String templateName) {
        return templateMap.computeIfAbsent(templateName, { key -> engine.createTemplate(key) })
    }

}


class MatchReq<T> {

    private T attribNativeVal;
    private String attribVal;
    private Class attribType;
    private String propName;
    private String vertexName;

    private Closure predicate;
    private Convert<T> conv;
    private StringBuffer sb = null;

    private excludeFromSearch;
    private excludeFromUpdate;

    static Closure convertPredicateFromStr(String predicateStr) {
        if ("eq".equals(predicateStr)) {
            return P.&eq
        } else if ("neq".equals(predicateStr)) {
            return P.&neq
        } else if ("gt".equals(predicateStr)) {
            return P.&gt
        } else if ("lt".equals(predicateStr)) {
            return P.&lt
        } else if ("gte".equals(predicateStr)) {
            return P.&gte
        } else if ("lte".equals(predicateStr)) {
            return P.&lte
        } else if ("textContains".equals(predicateStr)) {
            return org.janusgraph.core.attribute.Text.&textContains
        } else if ("textContainsPrefix".equals(predicateStr)) {
            return org.janusgraph.core.attribute.Text.&textContainsPrefix
        } else if ("textContainsRegex".equals(predicateStr)) {
            return org.janusgraph.core.attribute.Text.&textContainsRegex
        } else if ("textContainsFuzzy".equals(predicateStr)) {
            return org.janusgraph.core.attribute.Text.&textContainsFuzzy
        } else if ("textPrefix".equals(predicateStr)) {
            return org.janusgraph.core.attribute.Text.&textPrefix
        } else if ("textRegex".equals(predicateStr)) {
            return org.janusgraph.core.attribute.Text.&textRegex
        } else if ("textFuzzy".equals(predicateStr)) {
            return org.janusgraph.core.attribute.Text.&textFuzzy
        } else return P.eq;

    }

    MatchReq(String attribVals, Class<T> attribType, String propName, String vertexName, String predicateStr, boolean excludeFromSearch = false, boolean excludeFromUpdate = false, StringBuffer sb = null) {
        this.attribVal = attribVals
        this.attribType = attribType

        this.propName = propName
        this.vertexName = vertexName
        this.conv = new Convert<>(attribType)
        this.predicate = convertPredicateFromStr(predicateStr)

        this.sb = sb;

        this.excludeFromSearch = excludeFromSearch
        this.excludeFromUpdate = excludeFromUpdate

        sb?.append("\n In MatchReq($attribVals, $attribType, $propName, $vertexName, $predicateStr)")
        convertToNativeFormat()
    }

    // int compareTo(Object other) {
    //     this.propName <=> other.propName
    // }

    protected void convertToNativeFormat() {

//        Convert.fromString("asdf", this.attribType);

        if (this.attribType == String) {
            this.attribNativeVal = this.attribVal;
        } else {
            this.attribNativeVal = conv.fromString(this.attribVal, this.attribType, this.sb)


        }
    }


    def getExcludeFromSearch() {
        return excludeFromSearch
    }

    void setExcludeFromSearch(excludeFromSearch) {
        this.excludeFromSearch = excludeFromSearch
    }


    def getExcludeFromUpdate() {
        return excludeFromUpdate
    }

    void setExcludeFromUpdate(excludeFromUpdate) {
        this.excludeFromUpdate = excludeFromUpdate
    }

    T getAttribNativeVal() {
        return attribNativeVal
    }

    void setAttribNativeVal(T attribNativeVal) {
        this.attribNativeVal = attribNativeVal
    }

    String getAttribVal() {
        return attribVal
    }

    void setAttribVal(String attribVal) {
        this.attribVal = attribVal
    }

    Class getAttribType() {
        return attribType
    }

    void setAttribType(Class attribType) {
        this.attribType = attribType
    }

    String getPropName() {
        return propName
    }

    void setPropName(String propName) {
        this.propName = propName
    }

    String getVertexName() {
        return vertexName
    }

    void setVertexName(String vertexName) {
        this.vertexName = vertexName
    }

    Closure getPredicate() {
        return predicate
    }

    void setPredicate(Closure predicate) {
        this.predicate = predicate
    }

    @Override
    String toString() {
        return propName + '=' + attribNativeVal
    }
}


def matchVertices(gTrav = g, List<MatchReq> matchReqs, int maxHitsPerType, StringBuffer sb = null) {


    HashMap<String, List<Long>> vertexListsByVertexName = new HashMap();

    HashMap<String, List<MatchReq>> matchReqByVertexName = new HashMap<>();

    matchReqs.each {
        List<MatchReq> matchReqList = matchReqByVertexName.computeIfAbsent(it.vertexName, { k -> new ArrayList<>() });
        matchReqList.push(it)
        vertexListsByVertexName.computeIfAbsent(it.vertexName, { k -> new ArrayList<>() })

    }


    matchReqByVertexName.each { k, v ->

        def gtrav = gTrav

//        int expectedSizeOfQueries = v.unique { a, b -> a.propName <=> b.propName }.size()

        // HashMap<Object, MatchReq>  params = new HashMap<>();

        // NOTICE: MUST GET this to only do a Full match across all the elements in the property name;
        // otherwise, with large datasets, the other subsequences of smaller sizes may return loads of
        // false positives, and many false negatives (e.g. if a person lives in London, we may end up with
        // loads of hits for London, and with the cap, we may exclude the real match).

        // NOTICE2: LPPM 23Aug2018 -> actually, it's fine to have the smaller subsequences, as the higher ones
        // will produce higher counts anyway; otherwise, the NLP logic will be quite hard.

        def subs = v.subsequences()

        // sb?.append("\n $subs")


        subs.each { it ->

            // WARNING: it.unique changes the list; DONT call it as the first arg!
            // Also, we should always this lambda
            // comparator here rather than at the class so
            // the subsequences can do its job without repetition

            if (it.size() == it.unique { entry -> entry.propName }.size()) { //SEE NOTICEs ABOVE TO SEE WHY THIS was  COMMENTED OUT (LPPM - 21/08/2018)
//            if (it.size() == expectedSizeOfQueries) {

                def searchableItems = it.findAll { it2 -> !it2.excludeFromSearch }

                if (searchableItems.size() > 0) {
                    sb?.append("\ng.V().has('Metadata.Type.")?.append(k)?.append("',eq('")?.append(k)?.append("')")
                    gtrav = gTrav.V().has("Metadata.Type." + k, eq(k)).clone()

                    searchableItems.each { it2 ->
                        gtrav = gtrav.has(it2.propName, it2.predicate(it2.attribNativeVal)).clone()
                        sb?.append("\n     .has('")?.append(it2.propName)?.append("',")
                                ?.append(it2.predicate)?.append(",'")?.append(it2.attribNativeVal)?.append("')")


                    }
                    vertexListsByVertexName.get(k).addAll(gtrav.range(0, maxHitsPerType).id().toList() as Long[])
                    sb?.append("\n $it")

                }


            }

        }

        sb?.append('\n')?.append(vertexListsByVertexName)?.append("\n")


    }




    return [vertexListsByVertexName, matchReqByVertexName];

}


def getTopHits(HashMap<String, List<Long>> vertexListsByVertexName, String targetType, int countThreshold, StringBuffer sb = null) {
    def ids = vertexListsByVertexName.get(targetType) as Long[];

    return getTopHits(ids, countThreshold, sb)

}


def getTopHits(Long[] ids, int countThreshold, StringBuffer sb = null) {

    Map<Long, Integer> counts = ids.countBy { it }

    counts = counts.sort { a, b -> b.value <=> a.value }

    List<Long> retVal = new ArrayList<>()
    counts.each { k, v ->
        if (v >= countThreshold) {
            retVal.add(k)
        }

    }


    return retVal

}


def getOtherTopHits(Map<String, List<Long>> vertexListsByVertexName, String targetType, int countThreshold, StringBuffer sb = null) {

    Set<Long> otherIdsSet = new HashSet<>();

    vertexListsByVertexName.each { k, v ->
        if (k != targetType) {
            otherIdsSet.addAll(getTopHits(v as Long[], countThreshold, sb))
        }
    }

    return otherIdsSet;

}


def findMatchingNeighboursFromSingleRequired(gTrav = g, Long requiredTypeId, Set<Long> otherIds, StringBuffer sb = null) {


    def foundIds = gTrav.V(otherIds)
            .both()
            .hasId(requiredTypeId).id()
            .toSet() as Long[]

    sb?.append("\n in findMatchingNeighboursFromSingleRequired() - foundIds = $foundIds")

    return foundIds

}

def findMatchingNeighbours(gTrav = g, Set<Long> requiredTypeIds, Set<Long> otherIds, StringBuffer sb = null) {


    def foundIds = gTrav.V(otherIds)
            .both()
            .hasId(within(requiredTypeIds)).id()
            .toSet() as Long[]

    sb?.append("\n$foundIds")

    return foundIds

}

/*

 */

void addNewMatchRequest(Map<String, String> binding, List<MatchReq> matchReqs, String propValItem, Class nativeType, String propName, String vertexName, String predicate, boolean excludeFromSearch, boolean excludeFromUpdate, String postProcessor, String postProcessorVar, StringBuffer sb = null) {

    MatchReq mreq = null;

    if (nativeType == LocationAddress) {

        LocationAddress addr = LocationAddress.fromString(propValItem as String);

        Class nativeTypeAddrParts = String.class;

        addr.tokens.each { key, val ->

            val.each { it ->


                binding.put(postProcessorVar ?: "it", it);
                String processedVal = (postProcessor != null) ?
                        PVValTemplate.getTemplate((String) postProcessor).make(binding) :
                        it;


                mreq = new MatchReq(
                        (String) processedVal as String
                        , nativeTypeAddrParts
                        , (String) "${propName}.${key}" as String
                        , (String) vertexName
                        , (String) predicate
                        , (boolean) excludeFromSearch
                        , (boolean) excludeFromUpdate
                        , sb
                );

                if (mreq?.attribNativeVal != null) {
                    matchReqs.add(mreq)

                }
            }

        }


    } else {

        binding.put(postProcessorVar ?: "it", propValItem);

        String processedVal = (postProcessor != null) ?
                PVValTemplate.getTemplate((String) postProcessor).make(binding) :
                propValItem;

        mreq = new MatchReq(
                (String) processedVal as String
                , nativeType
                , (String) propName
                , (String) vertexName
                , (String) predicate
                , (boolean) excludeFromSearch
                , (boolean) excludeFromUpdate
                , sb

        );

        if (mreq?.attribNativeVal != null) {
            matchReqs.add(mreq)

        }
    }


}

def getMatchRequests(Map<String, String> currRecord, Object parsedRules, String rulesJsonStr, StringBuffer sb = null) {
    def binding = currRecord

    binding.put("original_request", rulesJsonStr)

    def rules = parsedRules

    List<MatchReq> matchReqs = new ArrayList<>(rules.vertices.size())

    JsonSlurper slurper = new JsonSlurper()

    rules.vertices.each { vtx ->

        String vertexName = vtx.label
        vtx.props.each { prop ->

            Class nativeType;

            if (prop.type == null) {
                nativeType = String.class
            } else {
                nativeType = Class.forName((String) prop.type)
            }

            String propName = prop.name

            String propVal = PVValTemplate.getTemplate((String) prop.val).make(binding)
            if (propVal != null && !"null".equals(propVal)) {
                String predicate = prop.predicate ?: "eq"


                if (nativeType.isArray()) {

                    nativeType = nativeType.getComponentType();

                    def propVals = slurper.parseText(propVal)


                    propVals.each { propValItem ->

                        addNewMatchRequest(
                                binding
                                , matchReqs
                                , (String) propValItem as String
                                , nativeType
                                , (String) propName
                                , (String) vertexName
                                , (String) predicate
                                , (boolean) prop.excludeFromSearch ? true : false
                                , (boolean) prop.excludeFromUpdate ? true : false
                                , (String) prop.postProcessor ?: null
                                , (String) prop.postProcessorVar ?: null
                                , sb
                        );


                    }


                } else {
                    addNewMatchRequest(
                            binding
                            , matchReqs
                            , (String) propVal
                            , nativeType
                            , (String) propName
                            , (String) vertexName
                            , (String) predicate
                            , (boolean) prop.excludeFromSearch ? true : false
                            , (boolean) prop.excludeFromUpdate ? true : false
                            , (String) prop.postProcessor ?: null
                            , (String) prop.postProcessorVar ?: null
                            , sb
                    );
                }


            }


        }


    }
    return matchReqs;

}


def getTopHit(g, Long[] potentialHitIDs, int numHitsRequiredForMatch, HashMap<String, List<Long>> matchIdsByVertexType, String vertexTypeStr, Map<String, List<EdgeRequest>> edgeReqsByVertexType, StringBuffer sb = null) {

    sb?.append("\nIn getTopHit() -- vertType = ${vertexTypeStr} ; potentialHitIDs = ${potentialHitIDs} ")
    Long[] topHits = getTopHits(potentialHitIDs as Long[], numHitsRequiredForMatch, sb)

    sb?.append("\nIn getTopHit() -- vertType = ${vertexTypeStr} ; topHits = ${topHits} ")

    Long topHit = null
    Integer numEdgesRequired = edgeReqsByVertexType.get(vertexTypeStr)?.size()

    if (numEdgesRequired != null && numEdgesRequired > 0) {
        if (topHits.size() > 0) {
            // Sanity check: we now have one or more candidates, so let's check
            // if this has conns to other vertices in our little world
            def otherTopHits = getOtherTopHits(matchIdsByVertexType, vertexTypeStr, 1, sb)

            int ilen = topHits.size()

            for (int i = 0; i < ilen; i++) {

                Long[] tempTopHits = findMatchingNeighboursFromSingleRequired(g, topHits[i] as Long, otherTopHits as Set<Long>, sb)
                if (tempTopHits?.size() > 0) {
                    topHit = tempTopHits[0]
                    break
                }
            }


        }
    } else {
        if (topHits.size() > 0) {
            topHit = topHits[0]
        }

    }

    sb?.append("\nIn getTopHit() -- vertType = ${vertexTypeStr} ; topHit  = ${topHit} ")

    return topHit;

}

def addNewVertexFromMatchReqs(g, String vertexTypeStr, List<MatchReq> matchReqsForThisVertexType, StringBuffer sb = null) {

    def localTrav = g

    localTrav = localTrav.addV(vertexTypeStr)
            .property('Metadata.Type.' + vertexTypeStr, vertexTypeStr)
            .property('Metadata.Type', vertexTypeStr)

    matchReqsForThisVertexType.each { it ->
        localTrav = localTrav.property(it.getPropName(), it.attribNativeVal)
    }

    Long retVal = localTrav.next().id() as Long

    sb?.append("\n in addNewVertexFromMatchReqs() - added new vertex of type ${vertexTypeStr}; id = ${retVal}")
    return retVal


}


def updateExistingVertexWithMatchReqs(g, Long vertexId, List<MatchReq> matchReqsForThisVertexType, StringBuffer sb = null) {

    def localTrav = g
    def deletionTrav = g
    sb?.append("\n in updateExistingVertexWithMatchReqs() - about to start Updating vertex of id ${vertexId}; ${matchReqsForThisVertexType}")

    localTrav = localTrav.V(vertexId)

    boolean atLeastOneUpdate = false;
    matchReqsForThisVertexType.each { it ->
        if (!it.excludeFromUpdate && it.attribNativeVal != null) {

            String propName = it.getPropName();
            sb?.append("\n in updateExistingVertexWithMatchReqs() - updating new vertex of id = ${vertexId} prop=${propName} val = ${it.attribNativeVal}")

            try {
                deletionTrav.V(vertexId).properties(it.getPropName()).drop().iterate()

            }
            catch (Throwable t) {
                sb?.append("\n in updateExistingVertexWithMatchReqs() - FAILED TO DELETE  = ${vertexId} prop=${propName} val = ${it.attribNativeVal}; err = $t")
            }
            localTrav = localTrav.property(propName, it.attribNativeVal)
            atLeastOneUpdate = true

        } else {
            sb?.append("\n in updateExistingVertexWithMatchReqs() - SKIPPING UPDATE either due to null value or excludeFromUpdate == ${it.excludeFromUpdate} ; vertexId = ${vertexId} prop=${it.propName} val = ${it.attribNativeVal} ")

        }
    }

    if (atLeastOneUpdate) {
        localTrav.iterate()
        sb?.append("\n in updateExistingVertexWithMatchReqs() - updated vertex with  id ${vertexId}")

    } else {
        sb?.append("\n in updateExistingVertexWithMatchReqs() - SKIPPED UPDATES for  vertex with id ${vertexId}")

    }

    // Long retVal = localTrav.next().id() as Long

    // return retVal


}


class EdgeRequest {

    String label;
    String fromVertexLabel;
    String toVertexLabel;

    EdgeRequest(String label, String fromVertexLabel, String toVertexLabel) {
        this.label = label
        this.fromVertexLabel = fromVertexLabel
        this.toVertexLabel = toVertexLabel
    }

    String getLabel() {
        return label
    }

    void setLabel(String label) {
        this.label = label
    }

    String getFromVertexLabel() {
        return fromVertexLabel
    }

    void setFromVertexLabel(String fromVertexLabel) {
        this.fromVertexLabel = fromVertexLabel
    }

    String getToVertexLabel() {
        return toVertexLabel
    }

    void setToVertexLabel(String toVertexLabel) {
        this.toVertexLabel = toVertexLabel
    }

    boolean equals(o) {
        if (this.is(o)) return true
        if (!(o instanceof EdgeRequest)) return false

        EdgeRequest that = (EdgeRequest) o

        if (fromVertexLabel != that.fromVertexLabel) return false
        if (label != that.label) return false
        if (toVertexLabel != that.toVertexLabel) return false

        return true
    }

    int hashCode() {
        int result
        result = (label != null ? label.hashCode() : 0)
        result = 31 * result + (fromVertexLabel != null ? fromVertexLabel.hashCode() : 0)
        result = 31 * result + (toVertexLabel != null ? toVertexLabel.hashCode() : 0)
        return result
    }

    String toString() {
        return "${label} = ($fromVertexLabel)->($toVertexLabel)"
    }
}


def parseEdges(def rules) {

    Map<String, List<EdgeRequest>> edgeReqsByVertexName = new HashMap<>()
    Set<EdgeRequest> edgeReqs = new HashSet<>()

    rules.edges.each { it ->
        String fromVertexLabel = it.fromVertexLabel
        String toVertexLabel = it.toVertexLabel
        String label = it.label

        EdgeRequest req = new EdgeRequest(label, fromVertexLabel, toVertexLabel);

        edgeReqs.add(req)
        fromEdgeList = edgeReqsByVertexName.computeIfAbsent(fromVertexLabel, { k -> new ArrayList<EdgeRequest>() })
        fromEdgeList.add(req)
        toEdgeList = edgeReqsByVertexName.computeIfAbsent(toVertexLabel, { k -> new ArrayList<EdgeRequest>() })
        toEdgeList.add(req)

    }

    return [edgeReqsByVertexName, edgeReqs]
}

def createEdges(gTrav, Set<EdgeRequest> edgeReqs, Map<String, Long> finalVertexIdByVertexName, StringBuffer sb = null) {

    edgeReqs.each { it ->

        sb?.append("\n in createEdges; edgeReq = $it ")

        sb?.append("\n in createEdges; finalVertexIdByVertexName = $finalVertexIdByVertexName ")

        Long fromId = finalVertexIdByVertexName.get(it.fromVertexLabel)
        Long toId = finalVertexIdByVertexName.get(it.toVertexLabel)

        sb?.append("\n in createEdges; from=$fromId; to=$toId ")

        if (fromId != null && toId != null) {

            def foundIds = gTrav.V(toId)
                    .both()
                    .hasId(within(fromId)).id()
                    .toSet() as Long[]

            sb?.append("\n in createEdges $foundIds")

            if (foundIds.size() == 0) {
                def fromV = gTrav.V(fromId)
                def toV = gTrav.V(toId)
                sb?.append("\n in createEdges about to create new Edges from  $fromId to $toId")
                gTrav.addE(it.label).from(fromV).to(toV).next()
            } else {
                sb?.append("\n in createEdges SKIPPING Edge creations")

            }
        } else {
            sb?.append("\n in createEdges SKIPPING Edge creations")

        }


    }
}

def ingestDataUsingRules(graph, g, Map<String, String> bindings, String jsonRules, StringBuffer sb = null) {

    def jsonSlurper = new JsonSlurper()
    def rules = jsonSlurper.parseText(jsonRules)

    def (edgeReqsByVertexName, edgeReqs) = parseEdges(rules.updatereq)
    trans = graph.tx()
    try {
        if (!trans.isOpen()) {
            trans.open()
        }

        def matchReqs = getMatchRequests(bindings, rules.updatereq, jsonRules, sb)
        def (matchIdsByVertexType, vertexListsByVertexName) = matchVertices(g, matchReqs, 10, sb);

        Map<String, Long> finalVertexIdByVertexName = new HashMap<>();
        matchIdsByVertexType.each { vertexTypeStr, potentialHitIDs ->

            List<MatchReq> matchReqsForThisVertexType = vertexListsByVertexName.get(vertexTypeStr)
//            int numHitsRequiredForMatch = matchReqsForThisVertexType?.size()
//
//            if (numHitsRequiredForMatch > 0) {
//                numHitsRequiredForMatch += (numHitsRequiredForMatch - 1)
//            }

            int numHitsRequiredForMatch = 1;

            Long topHit = getTopHit(g
                    , potentialHitIDs as Long[]
                    , (int) numHitsRequiredForMatch
                    , (HashMap<String, List<Long>>) matchIdsByVertexType
                    , (String) vertexTypeStr
                    , (Map<String, List<EdgeRequest>>) edgeReqsByVertexName
                    , sb)

            if (topHit != null) {

                updateExistingVertexWithMatchReqs(g, topHit, matchReqsForThisVertexType, sb)
                finalVertexIdByVertexName.put((String) vertexTypeStr, topHit)
            } else {
                Long newVertexId = addNewVertexFromMatchReqs(g, (String) vertexTypeStr, matchReqsForThisVertexType, sb)
                finalVertexIdByVertexName.put((String) vertexTypeStr, newVertexId)

            }


        }

        createEdges(g, (Set<EdgeRequest>) edgeReqs, (Map<String, Long>) finalVertexIdByVertexName, sb)






        trans.commit()
    } catch (Throwable t) {
        trans.rollback()
        throw t
    } finally {
        trans.close()
    }
}


def ingestRecordListUsingRules(graph, g, List<Map<String, String>> recordList, String jsonRules, StringBuffer sb = null) {

    def jsonSlurper = new JsonSlurper()
    def rules = jsonSlurper.parseText(jsonRules)

    def (edgeReqsByVertexName, edgeReqs) = parseEdges(rules.updatereq)
    trans = graph.tx()
    try {
        if (!trans.isOpen()) {
            trans.open()
        }

        for (Map<String, String> item in recordList) {

            def matchReqs = getMatchRequests(item, rules.updatereq, jsonRules, sb)
            def (matchIdsByVertexType, vertexListsByVertexName) = matchVertices(g, matchReqs, 10, sb);

            Map<String, Long> finalVertexIdByVertexName = new HashMap<>();
            matchIdsByVertexType.each { vertexTypeStr, potentialHitIDs ->

                List<MatchReq> matchReqsForThisVertexType = vertexListsByVertexName.get(vertexTypeStr)
                int numHitsRequiredForMatch = 1;
//                        matchReqsForThisVertexType?.size()
//
//                if (numHitsRequiredForMatch > 0) {
//                    numHitsRequiredForMatch += (numHitsRequiredForMatch - 1)
//                }

                Long topHit = getTopHit(g
                        , potentialHitIDs as Long[]
                        , (int) numHitsRequiredForMatch
                        , (HashMap<String, List<Long>>) matchIdsByVertexType
                        , (String) vertexTypeStr
                        , (Map<String, List<EdgeRequest>>) edgeReqsByVertexName
                        , sb)

                if (topHit != null) {

                    updateExistingVertexWithMatchReqs(g, topHit, matchReqsForThisVertexType, sb)
                    finalVertexIdByVertexName.put((String) vertexTypeStr, topHit)
                } else {
                    Long newVertexId = addNewVertexFromMatchReqs(g, (String) vertexTypeStr, matchReqsForThisVertexType, sb)
                    finalVertexIdByVertexName.put((String) vertexTypeStr, newVertexId)

                }


            }

            createEdges(g, (Set<EdgeRequest>) edgeReqs, (Map<String, Long>) finalVertexIdByVertexName, sb)


        }



        trans.commit()
    } catch (Throwable t) {
        trans.rollback()
        throw t
    } finally {
        trans.close()
    }
}

/*


def jsonSlurper = new JsonSlurper()
def listOfMaps = jsonSlurper.parseText '''
[ {
  "pg_ExistingCustomer" : "NO",
  "pg_FirstName" : "MICHAEL",
  "pg_LastName" : "PLATINI",
  "pg_ZipCode" : "B6 7NP",
  "pg_City" : "Birmingham",
  "pg_NumOfMarketingEmailSent" : "15",
  "pg_NumOpened" : "8",
  "pg_NumOfBrandEnagementEmailSent" : "8",
  "pg_NumTotalClickThrough" : "11",
  "pg_OpenOnDevice" : "Mobile",
  "pg_PrimaryEmailAddress" : "kiddailey@hotmail.com",
  "pg_PermissionToContactPrimary" : "Yes",
  "pg_SecondaryEmailID" : null,
  "pg_PermissionToContactSecondary" : null,
  "pg_DateofBirth" : "18/10/1969",
  "pg_MailBounced" : "1",
  "pg_Sex" : "Male",
  "pg_Unsubscribed" : "No",
  "pg_SpamReported" : "No",
  "pg_Policynumber" : null,
  "pg_PolicyType" : null,
  "pg_PolicyStatus" : null,
  "pg_ProspectStatus" : "Active",
  "pg_ClientManager" : null
}, {
  "pg_ExistingCustomer" : "NO",
  "pg_FirstName" : "JUDY",
  "pg_LastName" : "CAMEROON",
  "pg_ZipCode" : "B60 1DX",
  "pg_City" : "Bromsgrove",
  "pg_NumOfMarketingEmailSent" : "13",
  "pg_NumOpened" : "8",
  "pg_NumOfBrandEnagementEmailSent" : "7",
  "pg_NumTotalClickThrough" : "11",
  "pg_OpenOnDevice" : "Desktop",
  "pg_PrimaryEmailAddress" : "knorr@live.com",
  "pg_PermissionToContactPrimary" : "Yes",
  "pg_SecondaryEmailID" : "yeugo@hotmail.com",
  "pg_PermissionToContactSecondary" : "No",
  "pg_DateofBirth" : "04/12/1972",
  "pg_MailBounced" : "0",
  "pg_Sex" : "Female",
  "pg_Unsubscribed" : "No",
  "pg_SpamReported" : "No",
  "pg_Policynumber" : null,
  "pg_PolicyType" : null,
  "pg_PolicyStatus" : null,
  "pg_ProspectStatus" : "Active",
  "pg_ClientManager" : null
}, {
  "pg_ExistingCustomer" : "NO",
  "pg_FirstName" : "SACHIN",
  "pg_LastName" : "KUMAR",
  "pg_ZipCode" : "B742NH",
  "pg_City" : "Coldfield",
  "pg_NumOfMarketingEmailSent" : "11",
  "pg_NumOpened" : "8",
  "pg_NumOfBrandEnagementEmailSent" : "7",
  "pg_NumTotalClickThrough" : "13",
  "pg_OpenOnDevice" : "Mobile",
  "pg_PrimaryEmailAddress" : "mbswan@optonline.net",
  "pg_PermissionToContactPrimary" : "Yes",
  "pg_SecondaryEmailID" : null,
  "pg_PermissionToContactSecondary" : null,
  "pg_DateofBirth" : "12/09/1973",
  "pg_MailBounced" : "1",
  "pg_Sex" : "Male",
  "pg_Unsubscribed" : "No",
  "pg_SpamReported" : "No",
  "pg_Policynumber" : null,
  "pg_PolicyType" : null,
  "pg_PolicyStatus" : null,
  "pg_ProspectStatus" : "Active",
  "pg_ClientManager" : null
}, {
  "pg_ExistingCustomer" : "YES",
  "pg_FirstName" : "CORY",
  "pg_LastName" : "RHODES",
  "pg_ZipCode" : "DE75 7PQ",
  "pg_City" : "Heanor",
  "pg_NumOfMarketingEmailSent" : "10",
  "pg_NumOpened" : "9",
  "pg_NumOfBrandEnagementEmailSent" : "7",
  "pg_NumTotalClickThrough" : "11",
  "pg_OpenOnDevice" : "Mobile",
  "pg_PrimaryEmailAddress" : "dieman@yahoo.com",
  "pg_PermissionToContactPrimary" : "Yes",
  "pg_SecondaryEmailID" : null,
  "pg_PermissionToContactSecondary" : null,
  "pg_DateofBirth" : "05/04/1975",
  "pg_MailBounced" : "0",
  "pg_Sex" : "Male",
  "pg_Unsubscribed" : "No",
  "pg_SpamReported" : "No",
  "pg_Policynumber" : "98497047",
  "pg_PolicyType" : null,
  "pg_PolicyStatus" : "Open",
  "pg_ProspectStatus" : "Active",
  "pg_ClientManager" : "VVAP"
}, {
  "pg_ExistingCustomer" : "YES",
  "pg_FirstName" : "MICKEY",
  "pg_LastName" : "CRISTINO",
  "pg_ZipCode" : "NE70 7QG",
  "pg_City" : "Belford",
  "pg_NumOfMarketingEmailSent" : "13",
  "pg_NumOpened" : "9",
  "pg_NumOfBrandEnagementEmailSent" : "10",
  "pg_NumTotalClickThrough" : "14",
  "pg_OpenOnDevice" : "Mobile",
  "pg_PrimaryEmailAddress" : "jaxweb@sbcglobal.net",
  "pg_PermissionToContactPrimary" : "Yes",
  "pg_SecondaryEmailID" : null,
  "pg_PermissionToContactSecondary" : null,
  "pg_DateofBirth" : "31/08/1976",
  "pg_MailBounced" : "0",
  "pg_Sex" : "Female",
  "pg_Unsubscribed" : "No",
  "pg_SpamReported" : "No",
  "pg_Policynumber" : "10330435",
  "pg_PolicyType" : "Non- Renewable",
  "pg_PolicyStatus" : "Open",
  "pg_ProspectStatus" : "Active",
  "pg_ClientManager" : "WUFP"
}, {
  "pg_ExistingCustomer" : "NO",
  "pg_FirstName" : "HERMAN",
  "pg_LastName" : "STONE",
  "pg_ZipCode" : "HS8 5QX",
  "pg_City" : "South Uist",
  "pg_NumOfMarketingEmailSent" : "13",
  "pg_NumOpened" : "8",
  "pg_NumOfBrandEnagementEmailSent" : "9",
  "pg_NumTotalClickThrough" : "11",
  "pg_OpenOnDevice" : "Desktop",
  "pg_PrimaryEmailAddress" : "hermanab@live.com",
  "pg_PermissionToContactPrimary" : "Yes",
  "pg_SecondaryEmailID" : null,
  "pg_PermissionToContactSecondary" : null,
  "pg_DateofBirth" : "13/08/1979",
  "pg_MailBounced" : "0",
  "pg_Sex" : "Male",
  "pg_Unsubscribed" : "No",
  "pg_SpamReported" : "No",
  "pg_Policynumber" : null,
  "pg_PolicyType" : null,
  "pg_PolicyStatus" : null,
  "pg_ProspectStatus" : "Active",
  "pg_ClientManager" : null
}, {
  "pg_ExistingCustomer" : "YES",
  "pg_FirstName" : "JOHN",
  "pg_LastName" : "SMITH",
  "pg_ZipCode" : "PA15 4SY",
  "pg_City" : "Greenock",
  "pg_NumOfMarketingEmailSent" : "15",
  "pg_NumOpened" : "8",
  "pg_NumOfBrandEnagementEmailSent" : "9",
  "pg_NumTotalClickThrough" : "10",
  "pg_OpenOnDevice" : "Desktop",
  "pg_PrimaryEmailAddress" : "retoh@optonline.net",
  "pg_PermissionToContactPrimary" : "Yes",
  "pg_SecondaryEmailID" : null,
  "pg_PermissionToContactSecondary" : null,
  "pg_DateofBirth" : "08/04/1973",
  "pg_MailBounced" : "1",
  "pg_Sex" : "Male",
  "pg_Unsubscribed" : "No",
  "pg_SpamReported" : "No",
  "pg_Policynumber" : "10330434",
  "pg_PolicyType" : null,
  "pg_PolicyStatus" : "Open",
  "pg_ProspectStatus" : "Active",
  "pg_ClientManager" : "RIKR"
}, {
  "pg_ExistingCustomer" : "YES",
  "pg_FirstName" : "TRACY",
  "pg_LastName" : "NOAH",
  "pg_ZipCode" : "CM2 9HX",
  "pg_City" : "Chemsford",
  "pg_NumOfMarketingEmailSent" : "14",
  "pg_NumOpened" : "8",
  "pg_NumOfBrandEnagementEmailSent" : "9",
  "pg_NumTotalClickThrough" : "10",
  "pg_OpenOnDevice" : "Desktop",
  "pg_PrimaryEmailAddress" : "tromey@mac.com",
  "pg_PermissionToContactPrimary" : "Yes",
  "pg_SecondaryEmailID" : null,
  "pg_PermissionToContactSecondary" : null,
  "pg_DateofBirth" : "26/10/1982",
  "pg_MailBounced" : "0",
  "pg_Sex" : "Female",
  "pg_Unsubscribed" : "No",
  "pg_SpamReported" : "No",
  "pg_Policynumber" : "49949479",
  "pg_PolicyType" : null,
  "pg_PolicyStatus" : "Open",
  "pg_ProspectStatus" : "Active",
  "pg_ClientManager" : "JUDV"
}, {
  "pg_ExistingCustomer" : "NO",
  "pg_FirstName" : "JOHN",
  "pg_LastName" : "DAILEY",
  "pg_ZipCode" : "BH8 1  HM",
  "pg_City" : "Bournemouth",
  "pg_NumOfMarketingEmailSent" : "12",
  "pg_NumOpened" : "10",
  "pg_NumOfBrandEnagementEmailSent" : "10",
  "pg_NumTotalClickThrough" : "13",
  "pg_OpenOnDevice" : "Mobile",
  "pg_PrimaryEmailAddress" : "sabren@icloud.com",
  "pg_PermissionToContactPrimary" : "Yes",
  "pg_SecondaryEmailID" : "kuaoiio@gmail.com",
  "pg_PermissionToContactSecondary" : "Yes",
  "pg_DateofBirth" : "20/11/1984",
  "pg_MailBounced" : "2",
  "pg_Sex" : "Male",
  "pg_Unsubscribed" : "No",
  "pg_SpamReported" : "No",
  "pg_Policynumber" : null,
  "pg_PolicyType" : null,
  "pg_PolicyStatus" : null,
  "pg_ProspectStatus" : "Active",
  "pg_ClientManager" : null
}, {
  "pg_ExistingCustomer" : "NO",
  "pg_FirstName" : "KEITH",
  "pg_LastName" : "SAUNDERS",
  "pg_ZipCode" : "PH34 3ET",
  "pg_City" : "Speam Bridge",
  "pg_NumOfMarketingEmailSent" : "12",
  "pg_NumOpened" : "9",
  "pg_NumOfBrandEnagementEmailSent" : "10",
  "pg_NumTotalClickThrough" : "11",
  "pg_OpenOnDevice" : "Desktop",
  "pg_PrimaryEmailAddress" : "shazow@yahoo.com",
  "pg_PermissionToContactPrimary" : "Yes",
  "pg_SecondaryEmailID" : null,
  "pg_PermissionToContactSecondary" : null,
  "pg_DateofBirth" : "22/01/1987",
  "pg_MailBounced" : "1",
  "pg_Sex" : "Male",
  "pg_Unsubscribed" : "No",
  "pg_SpamReported" : "No",
  "pg_Policynumber" : null,
  "pg_PolicyType" : null,
  "pg_PolicyStatus" : null,
  "pg_ProspectStatus" : "Active",
  "pg_ClientManager" : null
}, {
  "pg_ExistingCustomer" : "NO",
  "pg_FirstName" : "MICHELLE",
  "pg_LastName" : "DAVIDSON",
  "pg_ZipCode" : "SG13 7EJ",
  "pg_City" : "Hertford",
  "pg_NumOfMarketingEmailSent" : "11",
  "pg_NumOpened" : "10",
  "pg_NumOfBrandEnagementEmailSent" : "8",
  "pg_NumTotalClickThrough" : "14",
  "pg_OpenOnDevice" : "Mobile",
  "pg_PrimaryEmailAddress" : "moxfulder@sbcglobal.net",
  "pg_PermissionToContactPrimary" : "Yes",
  "pg_SecondaryEmailID" : null,
  "pg_PermissionToContactSecondary" : null,
  "pg_DateofBirth" : "27/01/1987",
  "pg_MailBounced" : "0",
  "pg_Sex" : "Female",
  "pg_Unsubscribed" : "No",
  "pg_SpamReported" : "No",
  "pg_Policynumber" : null,
  "pg_PolicyType" : null,
  "pg_PolicyStatus" : null,
  "pg_ProspectStatus" : "Active",
  "pg_ClientManager" : null
} ]
'''



def rulesStr =  '''
{
  "updatereq":
  {

    "vertices":
	[
	  {
		"label": "Person"
	   ,"props":
			[
			  {
				"name": "Person.Full_Name"
			   ,"val": "${pg_FirstName?.toUpperCase() } ${pg_LastName?.toUpperCase()}"
			   ,"predicate": "textContains"
			  }
			 ,{
					"name": "Person.Last_Name"
			   ,"val": "${pg_LastName?.toUpperCase()}"
			  }
			 ,{
					"name": "Person.Date_Of_Birth"
			   ,"val": "${pg_DateofBirth}"
			   ,"type": "java.util.Date"
			  }
			 ,{
					"name": "Person.Gender"
			   ,"val": "${pg_Sex}"
			  }
			]
	  }
	 ,{
		"label": "Location.Address"
	,"props":
			[
			  {
				"name": "Location.Address.parser.postcode"
			   ,"val": "${com.pontusvision.utils.PostCode.format(pg_ZipCode)}"
			  }
			 ,{
					"name": "Location.Address.parser.city"
			   ,"val": "${pg_City?.toLowerCase()}"
			  }
			 ,{
					"name": "Location.Address.Post_Code"
			   ,"val": "${com.pontusvision.utils.PostCode.format(pg_ZipCode)}"
			   ,"excludeFromSearch": true
			  }
			]

	  }
	 ,{
		"label": "Object.Email_Address"
			,"props":
			[
			  {
				"name": "Object.Email_Address.Email"
			   ,"val": "${pg_PrimaryEmailAddress}"
			  }
			]

	  }
	 ,{
		"label": "Object.Insurance_Policy"
			,"props":
			[
			  {
				"name": "Object.Insurance_Policy.Number"
			   ,"val": "${pg_Policynumber}"
			  }
			 ,{
				"name": "Object.Insurance_Policy.Type"
			   ,"val": "${pg_PolicyType}"
			  }
			 ,{
					"name": "Object.Insurance_Policy.Status"
			   ,"val": "${pg_PolicyStatus}"
			   ,"excludeFromSearch": true
			  }

			]

	  }
	 ,{
		"label": "Event.Ingestion"
	   ,"props":
		[
		  {
			"name": "Event.Ingestion.Type"
		   ,"val": "MarketingEmailSystem"
		   ,"excludeFromSearch": true
		  }
		 ,{
			"name": "Event.Ingestion.Operation"
		   ,"val": "Upsert"
		   ,"excludeFromSearch": true
		  }
		 ,{
			"name": "Event.Ingestion.Domain_b64"
		   ,"val": "${original_request?.bytes?.encodeBase64()?.toString()}"
		   ,"excludeFromSearch": true
		  }
		 ,{
			"name": "Event.Ingestion.Metadata_Create_Date"
		   ,"val": "${new Date()}"
		   ,"excludeFromSearch": true
		  }

		]
	  }
	]
   ,"edges":
    [
      { "label": "Uses_Email", "fromVertexLabel": "Person", "toVertexLabel": "Object.Email_Address" }
     ,{ "label": "Lives", "fromVertexLabel": "Person", "toVertexLabel": "Location.Address"  }
     ,{ "label": "Has_Policy", "fromVertexLabel": "Person", "toVertexLabel": "Object.Insurance_Policy"  }
     ,{ "label": "Has_Ingestion_Event", "fromVertexLabel": "Person", "toVertexLabel": "Event.Ingestion"  }
    ]
  }
}
'''
// edgeLabel = createEdgeLabel(mgmt, "Has_Policy")

StringBuffer sb = new StringBuffer ()

// trigger the String.Mixin() call in the static c-tor

// sb.append("${PostCode.format(pg_ZipCode)}")
try{
    ingestRecordListUsingRules(graph, g, listOfMaps, rulesStr, sb)
}
catch (Throwable t){
    String stackTrace = org.apache.commons.lang.exception.ExceptionUtils.getStackTrace(t)

    sb.append("\n$t\n$stackTrace")


}
sb.toString()

// g.E().drop().iterate()
// g.V().drop().iterate()

// describeSchema()
// g.V()


*/