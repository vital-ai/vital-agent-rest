package ai.vital.agent.rest.webapp.webserver

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import groovy.json.JsonSlurper
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.ext.web.RoutingContext
import groovy.json.JsonOutput
import io.vertx.core.Vertx
import io.vertx.core.eventbus.DeliveryOptions
import io.vertx.core.eventbus.Message

class AgentSubmissionHandler implements Handler<RoutingContext> {
	
	private final static Logger log = LoggerFactory.getLogger(AgentSubmissionHandler.class)
	
	static JsonSlurper jsonParser = new JsonSlurper()
		
	static Vertx vertxInstance
		
	public AgentSubmissionHandler(Vertx vertxInstance) {
			
		this.vertxInstance = vertxInstance
	}
		
	@Override
	public void handle(RoutingContext ctx) {
			
		ctx.request().bodyHandler({ body ->
				
			log.info("AgentSubmissionHandler: " + body)
				
			try {
						
				Map jsonMap = jsonParser.parseText(body as String)
				
				// check incoming json
				
				boolean ok = true
				
				String requestJSON = JsonOutput.toJson(jsonMap)
	
				DeliveryOptions options = new DeliveryOptions()
	
				// long timeout
				options.setSendTimeout(600000)
	
				vertxInstance.eventBus().send("agentsubmission", requestJSON, options) { Future<Message> response ->
	
					if(response.succeeded()) {
	
						String resultJSON = response.result().body()
	
						Map resultMap = jsonParser.parseText(resultJSON)
	
						// change to status: ok, warn, error
						// warn would be for if the email already exists,
						// or some validation error
						
						// resultMap["status"] = "warn"
	
						// resultMap["message"] = "An error occurred. The email address is already registered.  If you need assistance, please email: info@chat.ai."
						
						def sendResponse = { ->
	
							ctx.response().end(JsonOutput.toJson(resultMap), 'UTF-8')
						}
	
						sendResponse()
	
						return
	
					}
					else {
	
						String message = 'error in agent submission.'
	
						def sendResponse = { ->
							Map r = [ok: false, message: message]
							ctx.response().end(JsonOutput.toJson(r), 'UTF-8')
						}
	
						sendResponse()
					}
				}
				
			} catch(Exception ex) {
				
				log.error("JSON Parse Error: " + ex.localizedMessage)
			
				boolean ok = false
				
				String message = 'AgentSubmissionHandle Error: ' + ex.localizedMessage
				
				def sendResponse = { ->
				Map r = [ok: ok, message: message]
					ctx.response().end(JsonOutput.toJson(r), 'UTF-8')
				}
				
				sendResponse()
			}
		})
		
	}
}
