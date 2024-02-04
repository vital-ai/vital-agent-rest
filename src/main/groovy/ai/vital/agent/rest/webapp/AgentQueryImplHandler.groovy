package ai.vital.agent.rest.webapp

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import org.slf4j.Logger
import groovy.json.JsonSlurper
import io.vertx.core.eventbus.Message
import ai.vital.vitalservice.VitalService
import ai.vital.vitalsigns.model.VitalApp
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.lang.groovy.GroovyVerticle
import java.util.List
import ai.vital.query.querybuilder.VitalBuilder
import ai.vital.service.vertx3.binary.ResponseMessage
import ai.vital.vitalservice.VitalStatus
import ai.vital.vitalservice.query.ResultElement
import ai.vital.vitalservice.query.ResultList
import ai.vital.vitalservice.query.VitalSelectQuery
import ai.vital.vitalservice.query.VitalSortProperty
import ai.vital.vitalsigns.VitalSigns
import ai.vital.vitalsigns.block.CompactStringSerializer
import ai.vital.vitalsigns.json.JSONSerializer
import ai.vital.vitalsigns.model.GraphMatch
import ai.vital.vitalsigns.model.GraphObject
import ai.vital.vitalsigns.model.VitalSegment
import ai.vital.vitalsigns.model.VitalServiceKey
import ai.vital.vitalsigns.model.property.IProperty
import ai.vital.vitalsigns.model.property.StringProperty
import ai.vital.vitalsigns.uri.URIGenerator
import ai.vital.weaviate.client.WeaviateClient
import java.util.Map.Entry
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.regex.Matcher
import java.util.regex.Pattern
import io.vertx.core.Handler
import ai.haley.api.HaleyAPI
import ai.haley.api.HaleyAPIDomainsValidation;
import ai.haley.api.session.HaleySession
import ai.haley.api.session.HaleyStatus
import ai.haley.kg.domain.KGAgent
import ai.haley.kg.domain.KGAgentSubmission
import ai.haley.kg.domain.properties.Property_hasKGAgentName
import ai.vital.domain.Account
import ai.vital.domain.Edge_hasUserLogin
import ai.vital.domain.Login
import ai.vital.domain.Login_PropertiesHelper
import ai.vital.virtuoso.client.VitalVirtuosoClient
import groovy.json.JsonOutput
import groovy.lang.Closure
import groovy.util.logging.Log
import com.google.common.collect.EvictingQueue
import com.vitalai.aimp.domain.AIMPMessage
import com.vitalai.aimp.domain.Channel
import com.vitalai.aimp.domain.MetaQLResultsMessage
import com.vitalai.aimp.domain.UserCommandMessage
import java.io.File
import org.slf4j.LoggerFactory
import ai.vital.vitalservice.factory.VitalServiceFactory
import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import org.apache.http.client.CredentialsProvider
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.auth.AuthScope
import org.apache.http.client.config.RequestConfig
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
// import org.apache.http.entity.mime.MultipartEntityBuilder
import java.util.stream.Collectors
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.entity.StringEntity
import org.apache.http.NameValuePair
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.io.UnsupportedEncodingException
import ai.vital.virtuoso.client.VitalVirtuosoClientManager

class AgentQueryImplHandler {

	private final static Logger log = LoggerFactory.getLogger(AgentQueryImplHandler.class)

	static JsonSlurper parser = new JsonSlurper()

	static ExecutorService executorService = Executors.newFixedThreadPool(1)

	static VitalApp app = VitalApp.withId("chat-saas")

	Integer instanceId
	
	public AgentQueryImplHandler(Integer instanceId) {
		
		this.instanceId = instanceId
	}
	
	public void handle(Message message) {

		log.info("Instance: ${instanceId} Handling: " + message)

		String body = message.body()

		log.info("Instance: ${instanceId} Process Message: " + body)

		Map queryMap = parser.parseText(body as String)

		// get all agents, paged
		
		// query agents via query terms in OR list, sorted by score, paged
		
		// query individual agent for details via agent URI
		
		// query include page number, defaulting to 1
		// page size is fixed at 12 (3 X 4)
		
		String queryType = queryMap["queryType"]
		
		if(queryType == null || queryType == "") {
			
			Map replyMap = [:]
			
			replyMap["status"] = "warn"
			
			replyMap["message"] = "An error occurred. queryType was not supplied."
			
			String replyJSON = JsonOutput.toJson(replyMap)
			
			message.reply(replyJSON)
			
			return
		}
		
		if(queryType != "all" && queryType != "query" && queryType != "details") {
			
			Map replyMap = [:]
			
			replyMap["status"] = "warn"
			
			replyMap["message"] = "An error occurred. An unknown queryType was supplied."
			
			String replyJSON = JsonOutput.toJson(replyMap)
			
			message.reply(replyJSON)
			
			return
		}
		
		if(queryType == "all") {
			
			handleAllQuery(message, queryMap)
			
			return
		}
		
		if(queryType == "query") {
			
			String queryString = queryMap["queryString"]
			
			if(queryString == null || queryString == "") {
				
				// revert to all case
				
				handleAllQuery(message, queryMap)
				
				return				
			}
			
			handleTermQuery(message, queryMap)
					
			return 
		}
		
		if(queryType == "details") {
			
			handleDetailsQuery(message, queryMap)
			
			return
		}
		
		Map replyMap = [:]
		
		replyMap["status"] = "warn"
		
		replyMap["message"] = "An error occurred. Unknown query criteria was supplied."
		
		String replyJSON = JsonOutput.toJson(replyMap)
		
		message.reply(replyJSON)
		
		return	
	}

	public void handleAllQuery(Message message, Map queryMap) {
		
		def r = new Callable() {

			public Object call() throws Exception {

				try {

					VitalSigns vs = VitalSigns.get()
					
				
					String serverName = vs.getConfig("virtuosoServer1Name")
					
					String user = vs.getConfig("virtuosoServer1User")
			
					String password = vs.getConfig("virtuosoServer1Password")
				
					String graphName = "http://vital.ai/graph/kgagent-graph-1"
												
					VitalVirtuosoClient virtuosoClient = VitalVirtuosoClientManager.getVitalVirtuosoClient(
						serverName,
						user,
						password,
						graphName)
										
					Integer pageNumber = getPageNumber(queryMap)
					
					Integer limit = 12
					
					Integer offset = pageNumber * 12
					
					VitalBuilder builder = new VitalBuilder()
					
					VitalSelectQuery sq = null
					
					ResultList results = null
										
					sq = builder.query{
						
						SELECT {
														
							value limit: limit
						
							value offset: 0
							
							value projection: true
									
							node_constraint { KGAgent.class }
							
							
														
						}
						
					}.toQuery()
											
					results = virtuosoClient.query(sq)
					
					log.info( "Query Status: " + results.status )
				
					Integer totalResults = results.totalResults
					
					log.info( "Total Results: " + totalResults )
						
					sq = builder.query{
						
						SELECT {
							
							value sortProperties: [VitalSortProperty.get(Property_hasKGAgentName .class, false)]
							
							value limit: limit
						
							value offset: offset
									
							node_constraint { KGAgent.class }
						}
						
					}.toQuery()
					
					results = virtuosoClient.query(sq)
					
					log.info( "Instance: ${instanceId} Query Status: " + results.status )
				
					Integer queryResults = results.results.size()
					
					log.info( "Instance: ${instanceId} Total Results: " + queryResults )
					
					JsonSlurper parser = new JsonSlurper()
					
					List<Map> resultList = []
					
					for(ResultElement re in results.results) {
						
						GraphObject g = re.graphObject
						
						String goJson = g.toJSON()
						
						Map goMap = parser.parseText(goJson)
						
						resultList.add(goMap)
					}
					
					Map replyMap = [:]

					replyMap["results"] = resultList
					
					replyMap["totalCount"] = totalResults
					
					replyMap["pageNumber"] = pageNumber
					
					replyMap["status"] = "ok"

					String replyJSON = JsonOutput.toJson(replyMap)

					message.reply(replyJSON)

					return "Ok"

				} catch(Exception ex) {

					log.error("Instance: ${instanceId} Got exception in agent query:" )

					log.error( ex.localizedMessage )

					Map replyMap = [

						errorMessage: ex.localizedMessage
					]

					replyMap["status"] = "error"

					String replyJSON = JsonOutput.toJson(replyMap)

					message.reply(replyJSON)

					return "Error"
				}
			}
		}
		
		FutureTask<String> future  = executorService.submit( r )

		return
	}
	
	public void handleTermQuery(Message message, Map queryMap) {

		def r = new Callable() {

			public Object call() throws Exception {
				
				Boolean topicSearch = queryMap["topicSearch"]
					
				Boolean keywordSearch = queryMap["keywordSearch"]
					
				Boolean bothSearch = queryMap["bothSearch"]
					
				if(topicSearch == null && keywordSearch == null && bothSearch == null) {
						
					keywordSearch = true		
				}
					
				// if keywordSearch == true, virtuoso
				// if topic or both, weaviate
						
				if(keywordSearch == true) {
						
					return handleVirtuoso(message, queryMap)
								
				} else {
						
					return handleWeaviate(message, queryMap)	
				}	
			}
		}

		FutureTask<String> future  = executorService.submit( r )

		return
	}
	
	public void handleDetailsQuery(Message message, Map queryMap) {

		def r = new Callable() {

			public Object call() throws Exception {

				try {

					VitalSigns vs = VitalSigns.get()
					
					
					String serverName = vs.getConfig("virtuosoServer1Name")
					
					String user = vs.getConfig("virtuosoServer1User")
			
					String password = vs.getConfig("virtuosoServer1Password")
				
					String graphName = "http://vital.ai/graph/kgagent-graph-1"
							
					VitalVirtuosoClient virtuosoClient = VitalVirtuosoClientManager.getVitalVirtuosoClient(
						serverName,
						user,
						password,
						graphName)
					
					String agentURI = queryMap["agentURI"]
				
					String agentIdentifier = queryMap["agentIdentifier"]
					
					String agentSlug = queryMap["agentSlug"]
					
					Boolean uriCase = false
					
					Boolean identifierCase = false
					
					if(agentURI != null && agentURI != "") {
						
						uriCase = true
					}
					
					if(agentIdentifier != null && agentIdentifier != "") {
						
						if(agentSlug != null && agentSlug != "") {
							
							identifierCase = true
						}
					}
					
					if(uriCase == false && identifierCase == false) {
						
						Map replyMap = [:]
						
						replyMap["status"] = "warn"
						
						replyMap["message"] = "An error occurred. The agent criteria was missing from the request."
						
						String replyJSON = JsonOutput.toJson(replyMap)
						
						message.reply(replyJSON)
						
						return "Error"
						
					}
					
					VitalBuilder builder = new VitalBuilder()
					
					VitalSelectQuery sq = null
					
					if(uriCase) {
						
						// TODO switch to resolve URI call
						sq = builder.query{
							
							SELECT {
								
								value limit: 1
							
								value offset: 0
								
								node_constraint { "URI eq ${agentURI}" }
										
								node_constraint { KGAgent.class }
							}
							
						}.toQuery()
						
					} else {
						
						sq = builder.query{
							
							SELECT {
								
								value limit: 1
							
								value offset: 0
								
								node_constraint { "kGAgentIdentifier eq ${agentIdentifier}" }
								
								node_constraint { "kGAgentNameSlug eq ${agentSlug}" }
										
								node_constraint { KGAgent.class }
							}
							
						}.toQuery()
					}
					
					ResultList results = virtuosoClient.query(sq)
					
					log.info( "Instance: ${instanceId} Query Status: " + results.status )
				
					if(results.results.size() != 1) {
						
						Map replyMap = [:]
						
						replyMap["status"] = "warn"
						
						replyMap["message"] = "No agent was found with the provided criteria."
						
						String replyJSON = JsonOutput.toJson(replyMap)
						
						message.reply(replyJSON)
						
						return "Error"			
					}
					
					KGAgent kgagent = results.results[0].graphObject
					
					JsonSlurper parser = new JsonSlurper()
					
					String kgagentJson = kgagent.toJSON()
					
					Map kgagentMap = parser.parseText(kgagentJson)
					
					Map replyMap = [:]

					replyMap["status"] = "ok"

					replyMap["agent"] = kgagentMap
					
					String replyJSON = JsonOutput.toJson(replyMap)

					message.reply(replyJSON)

					return "Ok"

				} catch(Exception ex) {

					log.error("Instance: ${instanceId} Got exception in agent query:" )

					log.error( ex.localizedMessage )

					Map replyMap = [

						errorMessage: ex.localizedMessage
					]

					replyMap["status"] = "error"

					String replyJSON = JsonOutput.toJson(replyMap)

					message.reply(replyJSON)

					return "Error"
				}
			}
		}

		FutureTask<String> future  = executorService.submit( r )

		return
	}
	
	public Integer getPageNumber(Map queryMap) {
		
		Integer pageNumber = 0
		
		if(queryMap["pageNumber"] != null) {
			
			def queryPageNumber = queryMap["pageNumber"]
			
			log.info("PageNumber: " + queryPageNumber)
			
			try {
			
				pageNumber = queryPageNumber as Integer
			
			} catch(Exception ex) {
				
				log.error(ex.localizedMessage)
			}
		}
		
		return pageNumber
	}
	
	public Object handleVirtuoso(Message message, Map queryMap) {
		
		try {
			
			Integer pageNumber = getPageNumber(queryMap)
			
			Integer limit = 12
			
			Integer offset = pageNumber * 12
			
			String queryString = queryMap["queryString"]
			
			String searchCategory = queryMap["categorySearch"]
	
			Boolean topicSearch = queryMap["topicSearch"]
			
			Boolean keywordSearch = queryMap["keywordSearch"]
			
			Boolean bothSearch = queryMap["bothSearch"]
			
			VitalSigns vs = VitalSigns.get()
				
			
			String serverName = vs.getConfig("virtuosoServer1Name")
			
			String user = vs.getConfig("virtuosoServer1User")
		
			String password = vs.getConfig("virtuosoServer1Password")
			
			String graphName = "http://vital.ai/graph/kgagent-graph-1"
						
			VitalVirtuosoClient virtuosoClient = VitalVirtuosoClientManager.getVitalVirtuosoClient(
				serverName,
				user,
				password,
				graphName)
				
			String[] queryTermArray = queryString.split(" ")
				
			List<String> queryTermList = []
				
			for(s in queryTermArray) {
					
				queryTermList.add(s)
			}
					
			String queryTermExp = queryTermList.collect { "'${it}'" }.join(' OR ')

/*				
			String sparqlCountQuery =
"""
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
PREFIX bif: <http://www.openlinksw.com/schemas/bif#>
PREFIX p0: <http://vital.ai/ontology/haley-ai-kg#>
SELECT ( COUNT (DISTINCT ?source1) as ?count ) WHERE {
  {
    ?source1 rdf:type p0:KGAgent . 
    ?source1 p0:vital__hasKGAgentName ?value2 . 
    ?value2 bif:contains "${queryTermExp}" OPTION (score ?score2) .
  } 
UNION
  {
    ?source1 rdf:type p0:KGAgent . 
    ?source1 p0:vital__hasKGAgentDescription ?value3 . 
    ?value3 bif:contains "${queryTermExp}" OPTION (score ?score3) . 
  }
BIND(COALESCE(?score3, 0) AS ?boundScore3)
BIND(COALESCE(?score2, 0) AS ?boundScore2)
}
"""
*/
			
		String sparqlCountQuery =
"""
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
PREFIX bif: <http://www.openlinksw.com/schemas/bif#>
PREFIX p0: <http://vital.ai/ontology/haley-ai-kg#>

SELECT ( COUNT (DISTINCT ?source1) as ?count)  WHERE {
  ?source1 rdf:type p0:KGAgent . 

  OPTIONAL {
    ?source1 p0:vital__hasKGAgentName ?value2 . 
    ?value2 bif:contains "${queryTermExp}" OPTION (score ?score2) .
  }

  OPTIONAL {
    ?source1 p0:vital__hasKGAgentDescription ?value3 . 
    ?value3 bif:contains "${queryTermExp}" OPTION (score ?score3) . 
  }

BIND(COALESCE(?score2, 0) AS ?boundScore2)
BIND(COALESCE(?score3, 0) AS ?boundScore3)
BIND((?boundScore2 + ?boundScore3) / 2.0 AS ?avgScore)
FILTER( ?avgScore > 0)
}
"""



		// log.info("QueryExpression: " + queryTermExp)

		ResultList results = virtuosoClient.querySparql(sparqlCountQuery)
		
		Integer count = 0
			
		for(ResultElement re in results.results) {
			
			GraphObject g = re.graphObject
											
			GraphMatch gm = g
					
			count = gm.count
		}
			
		/*	
		String sparqlQuery =
"""
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
PREFIX bif: <http://www.openlinksw.com/schemas/bif#>
PREFIX p0: <http://vital.ai/ontology/haley-ai-kg#>
SELECT ?source1, ?boundScore2, ?boundScore3, ((?boundScore2 + ?boundScore3) / 2 AS ?avgScore) WHERE {
  {
    ?source1 rdf:type p0:KGAgent . 
    ?source1 p0:vital__hasKGAgentName ?value2 . 
    ?value2 bif:contains "${queryTermExp}" OPTION (score ?score2) .
  } 
UNION
  {
    ?source1 rdf:type p0:KGAgent . 
    ?source1 p0:vital__hasKGAgentDescription ?value3 . 
    ?value3 bif:contains "${queryTermExp}" OPTION (score ?score3) . 
  }
BIND(COALESCE(?score3, 0) AS ?boundScore3)
BIND(COALESCE(?score2, 0) AS ?boundScore2)
}
ORDER BY DESC(?avgScore) 
OFFSET ${offset} LIMIT ${limit}
"""
*/
				

		String sparqlQuery =
"""
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
PREFIX bif: <http://www.openlinksw.com/schemas/bif#>
PREFIX p0: <http://vital.ai/ontology/haley-ai-kg#>

SELECT ?source1, ?boundScore2, ?boundScore3, ?avgScore WHERE {
  ?source1 rdf:type p0:KGAgent . 

  OPTIONAL {
    ?source1 p0:vital__hasKGAgentName ?value2 . 
    ?value2 bif:contains "${queryTermExp}" OPTION (score ?score2) .
  }

  OPTIONAL {
    ?source1 p0:vital__hasKGAgentDescription ?value3 . 
    ?value3 bif:contains "${queryTermExp}" OPTION (score ?score3) . 
  }

BIND(COALESCE(?score2, 0) AS ?boundScore2)
BIND(COALESCE(?score3, 0) AS ?boundScore3)
BIND((?boundScore2 + ?boundScore3) / 2.0 AS ?avgScore)
FILTER( ?avgScore > 0)
}
ORDER BY DESC(?avgScore)
OFFSET ${offset} LIMIT ${limit} 
"""

		results = virtuosoClient.querySparql(sparqlQuery)
											
		List<String> uriList = []
						
		for(ResultElement re in results.results) {
					
			GraphObject g = re.graphObject
							
			GraphMatch gm = g
							
			String kgAgentURI = gm.source1
					
			// log.info("Adding URI: " + kgAgentURI )
			
			uriList.add( kgAgentURI  )
							
			Integer score = gm.avgScore
		}
		
		
		// log.info("URI List Size: " + uriList.size() )
		
		// keep in sort order
										
		List<GraphObject> resolvedObjectList = virtuosoClient.resolveURIList(uriList)
		
		Map<String,GraphObject> resolvedMap = [:]	
		
		for(g in resolvedObjectList) {
			
		
			String uri = g.URI
				
			resolvedMap[uri] = g
			
		}
		
		JsonSlurper parser = new JsonSlurper()
		
		List<Map> resultList = []
		
		for(u in uriList) {
			
			GraphObject g = resolvedMap[u]
			
			if(g == null) { continue }
				
			// log.info("Resolved URI: " + g.URI )
			// log.info("Name URI: " + g.name )
						
			String goJson = g.toJSON()
					
			Map goMap = parser.parseText(goJson)
					
			resultList.add(goMap)

		}
		
		/*	
				
		for(g in resolvedObjectList) {
				
			log.info("Resolved URI: " + g.URI )
			log.info("Name URI: " + g.name )
			
						
			String goJson = g.toJSON()
					
			Map goMap = parser.parseText(goJson)
					
			resultList.add(goMap)
		}
		*/
		
		
				
		Map replyMap = [:]

		replyMap["results"] = resultList
				
		replyMap["totalCount"] = count
				
		replyMap["pageNumber"] = pageNumber
				
		replyMap["status"] = "ok"

		String replyJSON = JsonOutput.toJson(replyMap)

		message.reply(replyJSON)

		return "Ok"

	} catch(Exception ex) {

		log.error("Instance: ${instanceId} Got exception in agent query:" )

		log.error( ex.localizedMessage )

		Map replyMap = [

			errorMessage: ex.localizedMessage
		]

		replyMap["status"] = "error"

		String replyJSON = JsonOutput.toJson(replyMap)

		message.reply(replyJSON)

		return "Error"
	}

		
		
	}
	
	public Object handleWeaviate(Message message, Map queryMap) {

		try {

			VitalSigns vs = VitalSigns.get()

			Integer pageNumber = getPageNumber(queryMap)
			
			Integer limit = 12
			
			Integer offset = pageNumber * 12
			
			// This is fixed as there are always more vectors
			Integer totalCount = 48
			
			// requested offset is greater than total 
			if(offset > totalCount) {
				
				Map replyMap = [:]
				
				replyMap["results"] = []
				
				replyMap["totalCount"] = totalCount
				
				replyMap["pageNumber"] = pageNumber
				
				replyMap["status"] = "ok"
				
				String replyJSON = JsonOutput.toJson(replyMap)
				
				message.reply(replyJSON)
				
				return "Ok"
				
			}
			
			String queryString = queryMap["queryString"]
			
			JsonSlurper parser = new JsonSlurper()
		
			// String endpoint = "http://chat-saas-prod-weaviate-1.chat.ai:8080"
			
			String weaviateServer = vs.getConfig("weaviateServer1Name")
			
			String endpoint = "http://" + weaviateServer + ":8080"
			
			// "http://chat-saas-prod-weaviate-1.chat.ai:8080"
			
			log.info("Instance: ${instanceId} Weaviate Endpoint: " + endpoint)
			
			WeaviateClient weaviateClient  = WeaviateClient.getClient(endpoint)
			
			Boolean topicSearch = queryMap["topicSearch"]
			
			Boolean keywordSearch = queryMap["keywordSearch"]
			
			Boolean bothSearch = queryMap["bothSearch"]
	
			String vectorQuery = ""

			if(topicSearch == true) {
			
				vectorQuery = """{
  Get {
    KGAgent (
		limit: ${limit}
		offset: ${offset}
		nearText: {
          concepts: ["${queryString}"],
        }
	) 
	{
	  uri
      kGAgentName
	  kGAgentDescription
	  _additional {
		id
        vector
      }
    }
  }
}"""
	

			}
	  
			if(bothSearch == true) {

				vectorQuery = """{
			Get {
			  KGAgent (
				   hybrid: {
				  query: "${queryString}"
				}
				limit: ${limit}
				offset: ${offset}
			  )
			  {
				uri
				kGAgentName
				kGAgentDescription
				_additional {
				  id
				  vector
				}
			  }
			}
		  }"""
		  
			
			}

			
			
			List<String> uriList = []

			Map vectorQueryMap = [:]
			
			vectorQueryMap["query"] = vectorQuery
			
			String queryJSON = JsonOutput.toJson(vectorQueryMap)
		
			log.info("Instance: ${instanceId} VectorQuery: " + queryJSON)
			
			Date before = new Date()
		
			String queryResult = weaviateClient.query(queryJSON)
	
			Date after = new Date()
		
			long delta = after.getTime() - before.getTime()
		
			log.info("Instance: ${instanceId} Vector Query Delta (ms): " + delta)
		
			Double delta_seconds = ((Double) delta) / 1000.0d
		
			log.info("Instance: ${instanceId} Vector Query Delta(s): " + delta_seconds)
		
			Map queryResultMap = parser.parseText( queryResult )
			
			List<Map> queryResultList = queryResultMap["data"]["Get"]["KGAgent"]
			
			log.info("Instance: ${instanceId} ResultSize: " + queryResultList.size())
				
			for(Map qMap in queryResultList) {
					
				String uri = qMap["uri"]
				
				String kGAgentName = qMap["kGAgentName"]
				
				String kGAgentDescription = qMap["kGAgentDescription"]
				
				log.info( "URI: " + uri)
				
				uriList.add(uri)
				
				log.info( "Name: " + kGAgentName)
				
				// log.info("Description: " + kGAgentDescription)
			}
			
			// "chat-saas-prod-virtuoso-db-1.chat.ai"

			String serverName = vs.getConfig("virtuosoServer1Name")
			
			String user = vs.getConfig("virtuosoServer1User")

			String password = vs.getConfig("virtuosoServer1Password")

			String graphName = "http://vital.ai/graph/kgagent-graph-1"

			VitalVirtuosoClient virtuosoClient = VitalVirtuosoClientManager.getVitalVirtuosoClient(
					serverName,
					user,
					password,
					graphName)

			List<GraphObject> resolvedObjectList = virtuosoClient.resolveURIList(uriList)

			List<Map> resultList = []

			for(g in resolvedObjectList) {

				String goJson = g.toJSON()

				Map goMap = parser.parseText(goJson)

				resultList.add(goMap)
			}

			Map replyMap = [:]

			replyMap["results"] = resultList

			replyMap["totalCount"] = totalCount

			replyMap["pageNumber"] = pageNumber

			replyMap["status"] = "ok"

			String replyJSON = JsonOutput.toJson(replyMap)

			message.reply(replyJSON)

			return "Ok"
			

		} catch(Exception ex) {

			log.error("Instance: ${instanceId} Got exception in agent query:" )

			log.error( ex.localizedMessage )

			Map replyMap = [

				errorMessage: ex.localizedMessage
			]

			replyMap["status"] = "error"

			String replyJSON = JsonOutput.toJson(replyMap)

			message.reply(replyJSON)

			return "Error"
		}
	}
}
