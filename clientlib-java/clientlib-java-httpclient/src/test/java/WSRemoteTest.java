import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.URL;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.impl.conn.SchemeRegistryFactory;
import org.apache.http.impl.conn.SystemDefaultDnsResolver;

import org.daisy.pipeline.client.filestorage.JobStorage;
import org.daisy.pipeline.client.http.WS;
import org.daisy.pipeline.client.http.WSInterface;
import org.daisy.pipeline.client.models.Argument;
import org.daisy.pipeline.client.models.Job;
import org.daisy.pipeline.client.models.Script;
import org.daisy.pipeline.client.Pipeline2Exception;
import org.daisy.pipeline.client.utils.XML;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class WSRemoteTest extends PaxExamConfig {
	
	public WSRemoteTest() {
		super(false);
	}
	
	@Test
	public void testJobRemote() throws InterruptedException, ZipException, IOException {
		
		// pretend that the server is running on a remote machine with address http://example.org
		HttpClient httpclient = new DefaultHttpClient(
			new PoolingClientConnectionManager(
				SchemeRegistryFactory.createDefault(),
				-1,
				java.util.concurrent.TimeUnit.MILLISECONDS,
				new SystemDefaultDnsResolver() {
					@Override
					public InetAddress[] resolve(final String host) throws UnknownHostException {
						if (host.equalsIgnoreCase("localhost"))
							throw new UnknownHostException();
						else if (host.equals(WS_REMOTE_HOST))
							return super.resolve("localhost");
						else
							return super.resolve(host); }}));
		WSInterface ws = new WS(httpclient){};
		// assertNull(ws.alive());
		ws.setEndpoint(getEndpoint());
		assertFalse(ws.alive().localfs);
		Job job; {
			job = new Job();
			job.setId("1");
			Script script = ws.getScript("foo:script");
			job.setScript(script);
			File jobStorageDir = new File(BASEDIR, "target/tmp/client/jobs");
			JobStorage context = new JobStorage(job, jobStorageDir, null);
			Argument source = script.getArgument("source");
			source.set(new File(BASEDIR, "src/test/resources/input1.xml"), context);
			Argument option1 = script.getArgument("option-1");
			option1.set("three");
			Argument href = script.getArgument("href");
			href.set(new File(BASEDIR, "src/test/resources/input2.html"), context);
		}
		assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
		             "<jobRequest xmlns=\"http://www.daisy.org/ns/pipeline/data\">\n" +
		             
		             // FIXME: "0.0.0.0"
		             "   <script href=\"http://" + "0.0.0.0" + ":" + WS_PORT + "/" + WS_PATH + "/scripts/foo:script\"/>\n" +
		             "   <input name=\"source\">\n" +
		             "      <item value=\"input1.xml\"/>\n" +
		             "   </input>\n" +
		             "   <option name=\"option-1\">three</option>\n" +
		             "   <option name=\"href\">input2.html</option>\n" +
		             "</jobRequest>\n",
		             XML.toString(job.toJobRequestXml(false)));
		assertNull(job.validate());
		job.getJobStorage().save(false);
		ZipFile contextZip = new ZipFile(job.getJobStorage().makeContextZip());
		assertNotNull(contextZip.getEntry("input1.xml"));
		assertNotNull(contextZip.getEntry("input2.html"));
		contextZip.close();
		job = ws.postJob(job);
		assertNotNull(job);
		Thread.sleep(2000);
		job = ws.getJob(job.getId(), 0);
		assertEquals("The job is finished", Job.Status.DONE, job.getStatus());
		String result; {
			StringWriter writer = new StringWriter();
			assertEquals("http://" + "0.0.0.0" + ":" + WS_PORT + "/" + WS_PATH + "/jobs/" + job.getId(), job.getHref());
			String resultHref = job.getResults().get(job.getResult("output-dir")).get(0).href;
			assertEquals(job.getHref() + "/result/option/output-dir/idx/output-dir/result.xml", resultHref);
			IOUtils.copy(new URL(resultHref).openStream(), writer);
			result = writer.toString();
		}
		assertEquals("<result>" +
		             "<source><hello object=\"world\"/></source>" +
		             "<option name=\"href\" value=\""
		                 + new File(PIPELINE_DATA, "jobs/" + job.getId() + "/context/input2.html").toURI() + "\"/>" +
		             "</result>",
		             result);
	}
}
