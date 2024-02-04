package ai.vital.agent.rest.webapp

import io.vertx.core.Future
import io.vertx.lang.groovy.GroovyVerticle
import java.util.Map
import java.util.Map.Entry
import org.apache.commons.collections.map.LRUMap
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ai.vital.domain.Account
import ai.vital.domain.AdminLogin
import ai.vital.domain.Edge_hasUserLogin;
import ai.vital.domain.Login
import ai.vital.query.querybuilder.VitalBuilder
import ai.vital.service.vertx3.binary.ResponseMessage
import ai.vital.service.vertx3.handler.CallFunctionHandler
import ai.vital.vitalservice.VitalStatus
import ai.vital.vitalservice.factory.VitalServiceFactory
import ai.vital.vitalservice.json.VitalServiceJSONMapper
import ai.vital.vitalservice.query.ResultElement
import ai.vital.vitalservice.query.ResultList
import ai.vital.vitalservice.query.VitalGraphQuery
import ai.vital.vitalservice.query.VitalSelectQuery
import ai.vital.vitalsigns.block.CompactStringSerializer
import ai.vital.vitalsigns.model.GraphMatch
import ai.vital.vitalsigns.model.GraphObject
import ai.vital.vitalsigns.model.VitalApp
import ai.vital.vitalsigns.model.VitalSegment
import ai.vital.vitalsigns.model.property.IProperty
import ai.vital.vitalsigns.model.property.StringProperty
import com.vitalai.aimp.domain.AIMPMessage
import com.vitalai.haley.domain.HaleyAccount
import java.util.List
import ai.vital.vitalservice.VitalService
import ai.vital.vitalsigns.VitalSigns
import ai.vital.vitalsigns.json.JSONSerializer
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.regex.Matcher
import java.util.regex.Pattern
import io.vertx.core.Handler
import io.vertx.lang.groovy.GroovyVerticle
import ai.haley.api.HaleyAPI
import ai.haley.api.session.HaleySession
import ai.haley.api.session.HaleyStatus
import ai.vital.domain.Login_PropertiesHelper
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.lang.Closure
import groovy.util.logging.Log
import com.google.common.collect.EvictingQueue
import com.vitalai.aimp.domain.Channel
import com.vitalai.aimp.domain.MetaQLResultsMessage
import com.vitalai.aimp.domain.UserCommandMessage
import io.vertx.core.Vertx
import io.vertx.core.eventbus.Message
import java.io.File
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
import java.util.concurrent.CountDownLatch

class VertxUtils {
	
	private final static Logger log = LoggerFactory.getLogger(VertxUtils.class)
	
	public static String error_vital_service = 'error_vital_service'
	
	public static String error_not_logged_in = 'error_not_logged_in'
	
	public static String error_invalid_login_type = 'error_invalid_login_type'
	
	public final static String error_no_app_param = 'error_no_app_param'
	
	public final static String error_missing_param_root_key = 'error_missing_param_root_key'
	
	public final static String error_missing_param_service = 'error_missing_param_service'
	
	public final static String error_invalid_account_type = 'error_invalid_account_type'
	
	public final static String error_session_account_not_found = 'error_session_account_not_found'
	
	protected static VitalBuilder builder = new VitalBuilder()

	public static ResultList unpackGraphMatch(ResultList rl) {
		
		ResultList r = new ResultList();
				
		for(GraphObject g : rl) {
					
			if(g instanceof GraphMatch) {
						
				for(Entry<String, IProperty> p : g.getPropertiesMap().entrySet()) {
									
					IProperty unwrapped = p.getValue().unwrapped();
					if(unwrapped instanceof StringProperty) {
						GraphObject x = CompactStringSerializer.fromString((String) unwrapped.rawValue());
						if(x != null) r.getResults().add(new ResultElement(x, 1D));
					}
							
				}
					
			} else {
				throw new RuntimeException("Expected graph match objects only");
			}
					
		}
			
		return r;
	}
	
	public static List<VitalSegment> segmentsList(List<VitalSegment> segments) {
		
		Set<String> ids = new HashSet<String>()
		
		List<VitalSegment> out = []
		
		for(VitalSegment s : segments) {
			
			if(ids.add(s.segmentID.toString())) {
				out.add(s)
			}
			
		}
		
		return out	
	}
	
	public static boolean handleErrorResultList(Closure closure, ResponseMessage rm) {
		
		ResultList rl = new ResultList()
		
		if(rm.exceptionMessage) {
			rl.status = VitalStatus.withError(error_vital_service + " ${rm.exceptionType} - ${rm.exceptionMessage}")
			closure(rl)
			return true
		}
		
		rl = (ResultList) rm.response
		
		if(rl.status.status != VitalStatus.Status.ok) {
			
			closure(rl)
			
			return true
		}
		
		return false
		
	}
	
	
	public static String checkErrorResultListResponse(ResponseMessage rm) {
		
		if(rm.exceptionType) {
			return "${rm.exceptionType} - ${rm.exceptionMessage}"
		}
		
		ResultList rl = rm.response
		
		if(rl.status.status != VitalStatus.Status.ok) {
			String m = rl.status.message
			if(m == null || m.isEmpty()) m = '(unknown error)'
			return m
		}
		
		return null
		
	}
	
	public static String checkErrorStatusResponse(ResponseMessage rm) {
		
		if(rm.exceptionType) {
			return "${rm.exceptionType} - ${rm.exceptionMessage}"
		}
		
		VitalStatus status = rm.response
				
		if(status.status != VitalStatus.Status.ok) {
			String m = status.message
			if(m == null || m.isEmpty()) m = '(unknown error)'
			return m
		}
		
		return null
				
	}
	
	
	
	public static boolean handleErrorStatusResponse(Closure closure, ResponseMessage rm) {
		
		ResultList rl = new ResultList()
				
		if(rm.exceptionMessage) {
			rl.status = VitalStatus.withError(error_vital_service + " ${rm.exceptionType} - ${rm.exceptionMessage}")
			closure(rl)
			return true
		}
		
		VitalStatus status = rm.response
		
		if(status.status != VitalStatus.Status.ok) {
			rl.status = status
			closure(rl)
			return true
		}
		
		return false
				
	}


	public static errorResultList(Closure closure, String error) {
		ResultList rl = new ResultList()
		rl.status = VitalStatus.withError(error)
		closure(rl)
	}

	
	public static String maskPassword(String n) {
		
		if(n == null) return 'null'
		
		String output = "";

		for( int i = 0 ; i < n.length(); i++) {
				
			if(n.length() < 12 || ( i >= 4 && i < (n.length() - 4) ) ) {
				output += '*'
			} else {
				output += n.substring(i, i+1)
			}
				
		}
		
		return output
		
	}
	
}
