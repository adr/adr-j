package org.doble.commands;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Callable;

import org.doble.adr.*;
//import org.doble.annotations.Cmd;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * @author adoble
 *
 */
@Command(name = "init",
         description = "Initialises the directory of architecture decision records:\n" +
      			       " * creates a subdirectory of the current working directory\n" +
     			       " * creates the first ADR in that subdirectory, recording the decision to\n" +
     			       "   record architectural decisions with ADRs."
    	)
public class CommandInit implements Callable<Integer> {

	@Option(names = {"--madr"}, description = "Default to MADR. Uses docs/adr as directory without writing any configuration files.")
	private boolean madrFormat;

	@Option(names = { "-t", "--template" }, paramLabel = "TEMPLATE", description = "Template file used for ADRs.")
    private String template;

	@Option(names = { "-i", "--initial" }, paramLabel = "INITIALTEMPLATE",
			description = "A template for the initial ADR created during intialization")
    private String initialTemplate;

    @Parameters(arity = "0..1", paramLabel = "DOCDIR", description = "The directory to store the ADRs relative to "
    		                                                         + " the current directory."
    		                                                         + " Default is ${DEFAULT-VALUE}.")
    private String docPath = ADRProperties.defaultPathToAdrFiles;  //TODO change this to type path

    @ParentCommand
    private CommandADR commandADR;

	public Integer initializeMADR() throws Exception {
		Environment env = commandADR.getEnvironment();
		Path adrPath = env.pathOfCallOfAdrTool.resolve(ADRProperties.secondaryPathToAdrFiles);
		if (Files.exists(adrPath)) {
			env.err.println("Directory is already initialised for ADR.");
			return ADR.ERRORGENERAL;
		}

		Files.createDirectories(adrPath);

		Path madrDirectory = Paths.get("madr");
		Files.copy(madrDirectory.resolve("0000-use-markdown-architectural-decision-records"), adrPath);
		Files.copy(madrDirectory.resolve("template.md"), adrPath);

		return 0;
	}

	public Integer initializeNonMADR() throws Exception {
		int exitCode = 0;

		Environment env = commandADR.getEnvironment();
		Properties properties = new Properties();

		if (env.editorCommand == null) {
			String msg = "WARNING: Editor for the ADR has not been found in the environment variables.\n"
					+ "Have you set the environment variable EDITOR or VISUAL with the editor program you want to use?\n";
			env.err.println(msg);
			exitCode = ADR.ERRORENVIRONMENT;
		}

		properties.setProperty("docPath", docPath);
		if (template != null) properties.setProperty("templateFile", template.toString());
        if (initialTemplate != null) properties.setProperty("initialTemplateFile", initialTemplate.toString());

		Path adrPath = env.pathOfCallOfAdrTool.resolve(ADR.ADR_DIR_NAME);

        // Check if the directory has already been initialized
		if (Files.notExists(adrPath)) {
			Files.createDirectories(adrPath);
		} else {
			env.err.println("Directory is already initialised for ADR.");
			return ADR.ERRORGENERAL;
		}

		// Check that any template file specified really exists
		if (template != null) {
			Path templatePath = env.fileSystem.getPath(template);
			if (!Files.exists(templatePath)) {
				env.err.println("ERROR: The template file "
						+ template
						+ " does not exist!");
				return CommandLine.ExitCode.USAGE;
			}
		}

		// Check that any initial template file specifed really exists
		if (initialTemplate != null) {
			Path initialTemplatePath = env.fileSystem.getPath(initialTemplate);
			if (!Files.exists(initialTemplatePath)) {
				env.err.println("ERROR: The initial template file "
						+ initialTemplate
						+ " does not exist!");
				return CommandLine.ExitCode.USAGE;
			}
		}


		// Create a properties file
		Path propPath = adrPath.resolve("adr.properties");
		Files.createFile(propPath);

		BufferedWriter writer =  Files.newBufferedWriter(propPath);

		properties.store(writer, null);
		writer.close();

		// Now create the docs directory which contains the adr directory
		Path docsPath = env.pathOfCallOfAdrTool.resolve(properties.getProperty("docPath"));
		env.out.println("Creating ADR directory at " + docsPath);
		Files.createDirectories(docsPath);


		// If no template is specified and no initial template is specified create
		// an initial ADR using the default (Nygard) form
		if (template == null && initialTemplate == null) {
			Record record = new Record.Builder(docsPath)
					.template("rsrc:" + ADRProperties.defaultInitialTemplateName)
					.id(1)
					.name("Record architecture decisions")
					.date(new Date())
					.build();
			record.store();
		}

		// If a template is specified and an initial template is specified create an
		// initial ADR using the specified initial template
		if (template != null && initialTemplate != null) {
			Record record = new Record.Builder(docsPath)
					.template(initialTemplate)
					.id(1)
					.name("Record architecture decisions")
					.date(new Date())
					.build();
			record.store();
		}

		// If an initial template is specified, but no template give error message
		if (template == null && initialTemplate != null) {
			env.err.println("ERROR: Initial template [INITIALTEMPLATE] spceified, but no template [TEMPLATE]specified.  "
					+ "No initial ADR created!");
			env.err.println();
			exitCode = CommandLine.ExitCode.USAGE;
		}

		return exitCode;
	}

	/* (non-Javadoc)
	 * @see commands.Command#command(java.lang.String[])
	 */
	@Override
	public Integer call() throws Exception {
		if (this.madrFormat) {
			return this.initializeMADR();
		} else {
			return this.initializeNonMADR();
		}
	}

}
