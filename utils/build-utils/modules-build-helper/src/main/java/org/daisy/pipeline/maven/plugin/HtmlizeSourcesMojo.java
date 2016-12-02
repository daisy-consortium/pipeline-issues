package org.daisy.pipeline.maven.plugin;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FilenameFilter;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;

import org.codehaus.plexus.util.DirectoryScanner;

import org.daisy.maven.xproc.api.XProcEngine;
import org.daisy.maven.xproc.api.XProcExecutionException;

import static org.daisy.pipeline.maven.plugin.utils.asURI;
import static org.daisy.pipeline.maven.plugin.utils.relativize;

/**
 * @goal htmlize-sources
 * @requiresDependencyResolution compile
 */
public class HtmlizeSourcesMojo extends AbstractMojo {
	
	/**
	 * @parameter expression="${project.basedir}/src/main/resources"
	 * @required
	 */
	private File sourceDirectory;
	
	/**
	 * @parameter expression="${project.basedir}/src/main/resources/META-INF/catalog.xml"
	 * @required
	 */
	private File catalogXmlFile;
	
	/**
	 * @parameter
	 */
	private String includes;
	private final String defaultIncludes = "**/*.xpl,**/*.css";
	
	/**
	 * @parameter expression="${project.build.directory}/generated-resources/htmlize-sources"
	 * @required
	 */
	private File outputDirectory;
	
	/**
	 * @parameter expression="${project}"
	 */
	private MavenProject mavenProject;
	
	public void execute() throws MojoFailureException {
		try {
			@SuppressWarnings("unchecked")
			final XProcEngine engine = new CalabashWithPipelineModules(mavenProject.getCompileClasspathElements());
			List<File> sources = new ArrayList<File>();
			if (includes == null)
				includes = defaultIncludes;
			DirectoryScanner scanner = new DirectoryScanner();
			scanner.setBasedir(sourceDirectory);
			scanner.setIncludes(includes.replaceAll("\\s", "").split(",(?![^{]*})"));
			scanner.scan();
			for (String f : scanner.getIncludedFiles())
				sources.add(new File(sourceDirectory, f));
			final Map<FilenameFilter,Htmlizer> htmlizers = new HashMap<FilenameFilter,Htmlizer>(); {
				final Map<String,Map<String,String>> params
					= ImmutableMap.of("parameters",
					                  (Map<String,String>)ImmutableMap.of("catalog-xml-uri", asURI(catalogXmlFile).toASCIIString()));
				htmlizers.put(
					new FilenameFilter() {
						public boolean accept(File dir, String name) {
							return name.equals("catalog.xml") && dir.getName().equals("META-INF"); }},
					new Htmlizer() {
						public void run(Iterable<File> sources, File sourceDirectory, File outputDirectory) throws XProcExecutionException {
							List<String> sourcesAsURIs = new ArrayList<String>();
							for (File f : sources)
								sourcesAsURIs.add(asURI(f).toASCIIString());
								engine.run(asURI(HtmlizeSourcesMojo.class.getResource("/htmlize-sources/htmlize-catalog.xpl")).toASCIIString(),
								           ImmutableMap.of("sources", sourcesAsURIs),
								           null,
								           ImmutableMap.of("input-base-uri", asURI(sourceDirectory).toASCIIString(),
								                           "output-base-uri", asURI(outputDirectory).toASCIIString()),
								           params);
						}
					}
				);
				htmlizers.put(
					new FilenameFilter() {
						public boolean accept(File dir, String name) {
							return name.endsWith(".xpl"); }},
					new Htmlizer() {
						public void run(Iterable<File> sources, File sourceDirectory, File outputDirectory) throws XProcExecutionException {
							List<String> sourcesAsURIs = new ArrayList<String>();
							for (File f : sources)
								sourcesAsURIs.add(asURI(f).toASCIIString());
								engine.run(asURI(HtmlizeSourcesMojo.class.getResource("/htmlize-sources/htmlize-xproc.xpl")).toASCIIString(),
								           ImmutableMap.of("sources", sourcesAsURIs),
								           null,
								           ImmutableMap.of("input-base-uri", asURI(sourceDirectory).toASCIIString(),
								                           "output-base-uri", asURI(outputDirectory).toASCIIString()),
								           params);
						}
					}
				);
				htmlizers.put(
					new FilenameFilter() {
						public boolean accept(File dir, String name) {
							return name.endsWith(".css") || name.endsWith(".scss"); }},
					new Htmlizer() {
						public void run(Iterable<File> sources, File sourceDirectory, File outputDirectory) {
							for (File f : sources) {
								try {
									File outputFile = new File(outputDirectory, relativize(asURI(sourceDirectory), asURI(f)) + "/index.md");
									outputFile.getParentFile().mkdirs();
									BufferedReader reader = new BufferedReader(new FileReader(f));
									BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
									writer.write("<link rev=\"doc\" href=\"../" + f.getName() + "\"/>");
									writer.newLine();
									writer.newLine();
									if (f.getName().endsWith(".scss"))
										writer.write("~~~sass");
									else
										writer.write("~~~css");
									writer.newLine();
									String line;
									while ((line = reader.readLine()) != null) {
										writer.write(line);
										writer.newLine(); }
									writer.write("~~~");
									writer.newLine();
									writer.close();
								} catch (Exception e) {
									throw new RuntimeException("Error processing file " + f, e);
								}
							}
						}
					}
				);
			}
			Multimap<Htmlizer,File> index = Multimaps.index(
				sources,
				new Function<File,Htmlizer>() {
					public Htmlizer apply(File f) {
						for (Map.Entry<FilenameFilter,Htmlizer> kv : htmlizers.entrySet())
							if (kv.getKey().accept(f.getParentFile(), f.getName()))
								return kv.getValue();
						throw new RuntimeException("File type of " + f + " not recognized."); }});
			outputDirectory.mkdirs();
			for (Map.Entry<Htmlizer,Collection<File>> kv : index.asMap().entrySet())
				kv.getKey().run(kv.getValue(), sourceDirectory, outputDirectory);
		} catch (Throwable e) {
			throw new MojoFailureException(e.getMessage(), e);
		}
	}
	
	private static interface Htmlizer {
		public void run(Iterable<File> files, File sourceDirectory, File outputDirectory);
	}
}