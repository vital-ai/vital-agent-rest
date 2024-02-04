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
import java.util.Map.Entry
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.regex.Matcher
import java.util.regex.Pattern
import io.vertx.core.Handler
import ai.haley.api.HaleyAPI
import ai.haley.api.HaleyAPIDomainsValidation
import ai.haley.api.session.HaleySession
import ai.haley.api.session.HaleyStatus
import ai.haley.kg.domain.KGAgentSubmission
import ai.vital.domain.Account
import ai.vital.domain.Edge_hasUserLogin
import ai.vital.domain.Login
import ai.vital.domain.Login_PropertiesHelper
// import ai.vital.vitalservice.query.VitalGraphQuery
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

class AgentSubmissionImplHandler {
	
	private final static Logger log = LoggerFactory.getLogger(AgentSubmissionImplHandler.class)

	static JsonSlurper parser = new JsonSlurper()
	
	static ExecutorService executorService = Executors.newFixedThreadPool(1)

	static VitalApp app = VitalApp.withId("chat-saas")

	Integer instanceId
	
	public AgentSubmissionImplHandler(Integer instanceId) {
		
		this.instanceId = instanceId
	}
	
	public String cleanString(String inputString) {
		
		String cleanString = inputString
		
		if(cleanString == null) {
			
			cleanString = ""
		}
		
		cleanString = cleanString.trim()
		
		return cleanString
	}

	public void handle(Message message) {
		
		log.info("Instance: ${instanceId} Handling: " + message)
		
		String body = message.body()
		
		log.info("Instance: ${instanceId} Process Message: " + body)
		
		Map jsonMap = parser.parseText(body as String)
		
		// validate
		String grecaptcharesponse = jsonMap["g-recaptcha-response"]
		
		if(grecaptcharesponse == null) {
			
			Map replyMap = [:]
				
			replyMap["status"] = "warn"
				
			replyMap["message"] = "An error occurred. A reCAPTCHA Token was not included in the request.  If you need assistance, please email: agents@agentshop.ai."
				
			String replyJSON = JsonOutput.toJson(replyMap)
				
			message.reply(replyJSON)
				
			return
		}
		
		// need to encode this?
		// grecaptcharesponse
		 try {
			 
			 String grecaptcharesponse_encoded = URLEncoder.encode(grecaptcharesponse, StandardCharsets.UTF_8.toString())
				
			 // see:
			 // https://stackoverflow.com/questions/52416002/recaptcha-error-codes-missing-input-response-missing-input-secret-when-v
		
			 // can assume the key is URL safe
			 String secret = "fill-me-in"
		
			 String apiEndpoint = "https://www.google.com/recaptcha/api/siteverify?secret=${secret}&response=${grecaptcharesponse_encoded}"
			 
			 CloseableHttpClient httpclient = HttpClients.createDefault()
		
			 HttpPost httppost = new HttpPost ( apiEndpoint )
			
			 httppost.setHeader("Content-type", "application/x-www-form-urlencoded")
		
			 CloseableHttpResponse response = httpclient.execute( httppost )
		
			 String json_string = EntityUtils.toString( response.getEntity() )
		
			 response.close()
					
			 def prettyJSON = JsonOutput.prettyPrint(json_string)
		
			 log.info( "Result:\n" + prettyJSON )
		
			 Map resultMap = parser.parse(json_string.toCharArray())
		
			 Boolean success = resultMap["success"]
		
			 if(success == null || success == false) {
				 
				 Map replyMap = [:]
				 
				 replyMap["status"] = "warn"
				 
				 replyMap["message"] = "An error occurred while validating a reCAPTCHA Token. If you need assistance, please email: agents@agentshop.ai."
				 
				 String replyJSON = JsonOutput.toJson(replyMap)
				 
				 message.reply(replyJSON)
				 
				 return
			 }
			 
			 String channelege_ts = resultMap["challenge_ts"]
		
			 String hostname = resultMap["hostname"]
		
			 Double score = resultMap["score"]
		
			 if(score == null || score < 0.5) {
				 
				 Map replyMap = [:]
				 
				 replyMap["status"] = "warn"
				 
				 replyMap["message"] = "An error occurred while validating a reCAPTCHA Token. If you need assistance, please email: agents@agentshop.ai."
				 
				 String replyJSON = JsonOutput.toJson(replyMap)
				 
				 message.reply(replyJSON)
				 
				 return
			 }
			 
			 String action = resultMap["action"]
		
		 } catch(Exception ex) {
			 
			 Map replyMap = [:]
			 
			 replyMap["status"] = "warn"
			 
			 replyMap["message"] = "An error occurred. An error occurred while verifying a reCAPTCHA Token.  If you need assistance, please email: agents@agentshop.ai."
			 
			 String replyJSON = JsonOutput.toJson(replyMap)
			 
			 message.reply(replyJSON)
			 
			 return 
		 }
		
		String submissionName = jsonMap["submissionName"]
		String submissionDescription = jsonMap["submissionDescription"]
		String submissionCreator = jsonMap["submissionCreator"]
		String submissionComments = jsonMap["submissionComments"]
		String submissionURL = jsonMap["submissionURL"]
		
		
		String submitterName = jsonMap["submitterName"]
		String submitterEmail = jsonMap["submitterEmail"]
		
		submissionName = cleanString(submissionName)
		submissionDescription = cleanString(submissionDescription)
		submissionCreator = cleanString(submissionCreator)
		submissionComments = cleanString(submissionComments)
		submissionURL = cleanString(submissionURL)
		
		submitterName = cleanString(submitterName)
		submitterEmail = cleanString(submitterEmail)
		
		if(submissionName == null || submissionName == "") {
			
			Map replyMap = [:]
			
			replyMap["status"] = "warn"
			
			replyMap["message"] = "An error occurred. The agent name field appears empty. If you need assistance, please email: agents@agentshop.ai."
			
			String replyJSON = JsonOutput.toJson(replyMap)
			
			message.reply(replyJSON)
		
			return
		}
		
		if(submissionDescription == null || submissionDescription == "") {
			
			Map replyMap = [:]
			
			replyMap["status"] = "warn"
			
			replyMap["message"] = "An error occurred. The agent description field appears empty. If you need assistance, please email: agents@agentshop.ai."
			
			String replyJSON = JsonOutput.toJson(replyMap)
			
			message.reply(replyJSON)
		
			return
		}
		
		if(submissionCreator == null || submissionCreator == "") {
			
			Map replyMap = [:]
			
			replyMap["status"] = "warn"
			
			replyMap["message"] = "An error occurred. The agent creator field appears empty. If you need assistance, please email: agents@agentshop.ai."
			
			String replyJSON = JsonOutput.toJson(replyMap)
			
			message.reply(replyJSON)
		
			return
		}
		
		if(submissionURL == null || submissionURL == "") {
			
			Map replyMap = [:]
			
			replyMap["status"] = "warn"
			
			replyMap["message"] = "An error occurred. The agent URL field appears empty. If you need assistance, please email: agents@agentshop.ai."
			
			String replyJSON = JsonOutput.toJson(replyMap)
			
			message.reply(replyJSON)
		
			return
		}
		
		if(submitterName == null || submitterName == "") {
			
			Map replyMap = [:]
			
			replyMap["status"] = "warn"
			
			replyMap["message"] = "An error occurred. The submitter name field appears empty. If you need assistance, please email: agents@agentshop.ai."
			
			String replyJSON = JsonOutput.toJson(replyMap)
			
			message.reply(replyJSON)
		
			return
		}
		
		if(submitterEmail == null || submitterEmail == "") {
			
			Map replyMap = [:]
			
			replyMap["status"] = "warn"
			
			replyMap["message"] = "An error occurred. The submitter email field appears empty. If you need assistance, please email: agents@agentshop.ai."
			
			String replyJSON = JsonOutput.toJson(replyMap)
			
			message.reply(replyJSON)
		
			return
		}
		
		
		def r = new Callable() {
			
			public Object call() throws Exception {
			
				try {
								
					Map replyMap = [:]
					
					// put a synchronized block here to avoid inserting in parallel?
											
					KGAgentSubmission agentSubmission = new KGAgentSubmission()
					
					agentSubmission.URI = URIGenerator.generateURI(app, KGAgentSubmission.class)
				
					agentSubmission.kGAgentSubmissionName = submissionName
					
					agentSubmission.kGAgentSubmissionDescription = submissionDescription
					
					agentSubmission.kGAgentSubmissionDateTime = new Date()
					
					agentSubmission.kGAgentSubmissionCreator = submissionCreator
					
					agentSubmission.kGAgentSubmissionSubmitterName = submitterName
					
					agentSubmission.kGAgentSubmissionSubmitterEmail = submitterEmail
					
					agentSubmission.kGAgentSubmissionReviewed = false
					
					agentSubmission.kGAgentSubmissionComments = submissionComments
			
					agentSubmission.kGAgentSubmissionURL = submissionURL
				
					VitalSegment agentshop_segment = null
					
					AgentShopVerticle.service.listSegments().each{
						
						VitalSegment segment ->
						
							log.info( "Segment: " + segment.URI + " " + segment.segmentID)
								
							if(segment.segmentID.toString()== "agentshop") {
								agentshop_segment = segment
							}
					}
					
					ResultList insertResult = AgentShopVerticle.service.insert(agentshop_segment, agentSubmission)
					
					log.info( "Insert Results: " + insertResult.status )
										
					if(insertResult.status.status != VitalStatus.Status.ok) {
												
						replyMap["status"] = "warn"
						
						replyMap["message"] = "An error occurred while saving the Agent record. If you need assistance, please email: agents@agentshop.ai."
						
						String replyJSON = JsonOutput.toJson(replyMap)
						
						message.reply(replyJSON)
						
						return "Error"
					}
					
					replyMap["status"] = "ok"
					
					String replyJSON = JsonOutput.toJson(replyMap)
			
					message.reply(replyJSON)
			
					return "Ok"
			
				} catch(Exception ex) {
			
					log.error("Got exception inserting agent submission:" )
			
					log.error( ex.localizedMessage )
							
					Map replyMap = [
											
						submissionName: submissionName,
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
	}
}