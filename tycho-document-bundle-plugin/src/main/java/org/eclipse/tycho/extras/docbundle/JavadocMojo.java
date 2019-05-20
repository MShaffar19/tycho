/*******************************************************************************
 * Copyright (c) 2013, 2019 IBH SYSTEMS GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBH SYSTEMS GmbH - initial API and implementation
 *******************************************************************************/
package org.eclipse.tycho.extras.docbundle;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.toolchain.ToolchainManager;
import org.codehaus.plexus.util.FileUtils;
import org.eclipse.tycho.classpath.ClasspathEntry;
import org.eclipse.tycho.core.BundleProject;
import org.eclipse.tycho.core.TychoProject;
import org.eclipse.tycho.core.osgitools.BundleReader;

/**
 * Create the javadoc based API reference for this bundle <br/>
 * This mojo creates the javadoc documentation by calling the javadoc application from the command
 * line. In addition it creates a ready to include toc-xml file for the Eclipse Help system. <br/>
 * The sources for creating the javadoc are generated automatically based on the dependency that
 * this project has. As dependency you can specify any other maven project, for example the feature
 * project that references you other bundles. Included features will be added to the list.
 * 
 * @since 0.20.0
 */
@Mojo(name = "javadoc", defaultPhase = LifecyclePhase.PROCESS_CLASSES, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, threadSafe = false)
public class JavadocMojo extends AbstractMojo {
    /**
     * The directory where the javadoc content will be generated
     * 
     */
    @Parameter(property = "outputDirectory", defaultValue = "${project.build.directory}/reference/api", required = true)
    private File outputDirectory;

    /**
     * The base output directory
     */
    @Parameter(property = "basedir", required = true, readonly = true)
    private File basedir;

    /**
     * The build directory where temporary build files will be placed
     */
    @Parameter(property = "project.build.directory", required = true)
    private File buildDirectory;

    /**
     * An option to clean out the whole outputDirectory first.
     */
    @Parameter(property = "cleanFirst", defaultValue = "true")
    private boolean cleanFirst;

    @Component
    private ToolchainManager toolchainManager;

    @Parameter(property = "session", required = true, readonly = true)
    private MavenSession session;

    @Parameter(property = "reactorProjects", required = true, readonly = true)
    protected List<MavenProject> reactorProjects;

    /**
     * The scopes that the dependencies must have in order to be included
     */
    @Parameter(property = "scopes", defaultValue = "compile,provided")
    private Set<String> scopes = new HashSet<>();

    /**
     * Maven module types that will be used to include the source
     */
    @Parameter(property = "sourceTypes", defaultValue = "eclipse-plugin")
    private Set<String> sourceTypes = new HashSet<>();

    /**
     * Options for calling the javadoc application. Possible options are (all options are optional):
     * <ul>
     * <li><tt>ignoreError</tt>, specifies if errors calling javadoc should be ignored</li>
     * <li><tt>doclet</tt>, used as javadoc <tt>-doclet</tt> parameter</li>
     * <li><tt>docletArtifacts</tt>, dependencies will be resovled and added as <tt>-docletpath</tt>
     * parameter</li>
     * <li><tt>encoding</tt>, used as javadoc <tt>-encoding</tt> parameter (default:
     * <tt>${project.build.sourceEncoding}</tt></li>
     * <li><tt>additionalArguments</tt>, a list of additional arguments passed to javadoc</li>
     * <li><tt>includes</tt>/<tt>excludes</tt>, the list of names of packages to be included in or
     * excluded from JavaDoc processing; use '<tt>*</tt>' character as wildcard</li>
     * </ul>
     * Example configuration:
     * 
     * <pre>
     * &lt;configuration&gt;
     *    &lt;javadocOptions&gt;
     *       &lt;ignoreError&gt;false&lt;/ignoreError&gt;
     *       &lt;encoding&gt;UTF-8&lt;/encoding&gt;
     *       &lt;doclet&gt;foo.bar.MyDoclet&lt;/doclet&gt;
     *       &lt;docletArtifacts&gt;
     *          &lt;docletArtifact&gt;
     *             &lt;groupId&gt;foo.bar&lt;/groupId&gt;
     *             &lt;artifactId&gt;foo.bar.mydocletartifact&lt;/artifactId&gt;
     *             &lt;version&gt;1.0&lt;/version&gt;
     *          &lt;/docletArtifact&gt;
     *       &lt;/docletArtifacts&gt;
     *       &lt;includes&gt;
     *          &lt;include&gt;com.example.*&lt;/include&gt;
     *       &lt;/includes&gt;
     *       &lt;excludes&gt;
     *          &lt;exclude&gt;com.example.internal.*&lt;/exclude&gt;
     *       &lt;/excludes&gt;   
     *       &lt;additionalArguments&gt;
     *          &lt;additionalArgument&gt;-windowtitle "The Window Title"&lt;/additionalArgument&gt;
     *          &lt;additionalArgument&gt;-nosince&lt;/additionalArgument&gt;
     *       &lt;/additionalArguments&gt;
     *    &lt;/javadocOptions&gt;
     * &lt;/configuration&gt;
     * </pre>
     */
    @Parameter(property = "javadocOptions")
    private JavadocOptions javadocOptions = new JavadocOptions();

    /**
     * Options for creating the toc files.
     * <ul>
     * <li><tt>mainLabel</tt>, specifies the main label of the toc file (default: "API Reference")
     * </li>
     * <li><tt>mainFilename</tt>, specifies the filename of the TOC file (default:
     * "overview-summary.html")
     * </ul>
     * Example configuration:
     * 
     * <pre>
     * &lt;configuration&gt;
     *    &lt;tocOptions&gt;
     *       &lt;mainLabel&gt;My own label&lt;/mainLabel&gt;
     *       &lt;mainFilename&gt;myOverviewSummary.html&lt;/mainFilename&gt;
     *    &lt;/tocOptions&gt;
     * &lt;/configuration&gt;
     * </pre>
     */
    @Parameter(property = "tocOptions")
    private TocOptions tocOptions = new TocOptions();

    /**
     * Set this property to true to skip the generation of the Eclipse TOC files.
     */
    @Parameter(property = "skipTocGen", defaultValue = "false")
    private boolean skipTocGen = false;

    /**
     * The output location of the toc file.<br/>
     * This file will be overwritten.
     */
    @Parameter(property = "tocFile", defaultValue = "${project.build.directory}/tocjavadoc.xml")
    private File tocFile;

    @Parameter(property = "project.build.sourceEncoding", readonly = true)
    private String projectBuildSourceEncoding;

    @Component
    private BundleReader bundleReader;

    @Component
    private DocletArtifactsResolver docletArtifactsResolver;

    @Component(role = TychoProject.class)
    private Map<String, TychoProject> projectTypes;

    public void setTocOptions(TocOptions tocOptions) {
        this.tocOptions = tocOptions;
    }

    public void setSourceTypes(final Set<String> sourceTypes) {
        this.sourceTypes = sourceTypes;
    }

    public void setScopes(final Set<String> scopes) {
        this.scopes = scopes;
    }

    public void setJavadocOptions(final JavadocOptions javadocOptions) {
        this.javadocOptions = javadocOptions;
    }

    public void execute() throws MojoExecutionException {
        getLog().info("Scopes: " + this.scopes);
        getLog().info("Output directory: " + this.outputDirectory);
        getLog().info("BaseDir: " + this.basedir);

        if (this.cleanFirst) {
            getLog().info("Cleaning up first");
            cleanUp();
        }

        // if no encoding is set, fall back to ${project.build.sourceEncoding}
        if (javadocOptions.getEncoding() == null) {
            javadocOptions.setEncoding(projectBuildSourceEncoding);
        }

        final JavadocRunner runner = new JavadocRunner();
        runner.setLog(getLog());
        runner.setOutput(this.outputDirectory);
        runner.setBuildDirectory(this.buildDirectory);
        runner.setToolchainManager(this.toolchainManager);
        runner.setSession(this.session);
        runner.setDocletArtifactsResolver(docletArtifactsResolver);

        final GatherManifestVisitor gmv = new GatherManifestVisitor();
        visitProjects(this.session.getCurrentProject().getDependencies(), this.scopes, gmv);

        final GatherSourcesVisitor gsv = new GatherSourcesVisitor();
        visitProjects(this.session.getCurrentProject().getDependencies(), this.scopes, gsv);

        getLog().info(String.format("%s source folders", gsv.getSourceFolders().size()));
        for (final File file : gsv.getSourceFolders()) {
            getLog().info("Source folder: " + file);
        }

        final GatherClasspathVisitor gcv = new GatherClasspathVisitor();
        visitProjects(this.session.getCurrentProject().getDependencies(), this.scopes, gcv);

        final Collection<String> cp = gcv.getClassPath();

        getLog().info(String.format("%s classpath deps", cp.size()));
        for (final String ele : cp) {
            getLog().info("CP: " + ele);
        }

        runner.setBundleReader(this.bundleReader);
        runner.setOptions(this.javadocOptions);
        runner.setManifestFiles(gmv.getManifestFiles());
        runner.setSourceFolders(gsv.getSourceFolders());
        runner.setClassPath(cp);

        // Setup toc writer

        final TocWriter tocWriter = new TocWriter();
        tocWriter.setOptions(this.tocOptions);
        tocWriter.setJavadocDir(this.outputDirectory);
        tocWriter.setBasedir(this.basedir);
        tocWriter.setLog(getLog());

        try {
            runner.run();
            if (!skipTocGen) {
                tocWriter.writeTo(this.tocFile);
            }
        } catch (final Exception e) {
            throw new MojoExecutionException("Failed to run javadoc", e);
        }
    }

    private void cleanUp() throws MojoExecutionException {
        if (!this.outputDirectory.exists()) {
            return;
        }

        try {
            FileUtils.deleteDirectory(this.outputDirectory);
        } catch (final IOException e) {
            throw new MojoExecutionException("Failed to clean output directory", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void visitProjects(final List<?> dependencies, final Set<String> scopes, final ProjectVisitor visitor)
            throws MojoExecutionException {
        for (final Dependency dep : (List<Dependency>) dependencies) {
            getLog().debug("Dependency: " + dep + " / scope=" + dep.getScope());

            final String scope = dep.getScope();

            if (scopes.contains(scope)) {
                visitDeps(dep, visitor, scopes);
            }
        }
    }

    private interface ProjectVisitor {
        public void visit(MavenProject project) throws MojoExecutionException;
    }

    private class GatherSourcesVisitor implements ProjectVisitor {
        private final Set<File> sourceFolders = new HashSet<>();

        @Override
        public void visit(final MavenProject project) {
            if (JavadocMojo.this.sourceTypes.contains(project.getPackaging())) {
                for (final String root : (Collection<String>) project.getCompileSourceRoots()) {
                    getLog().debug("\tAdding source root: " + root);
                    final File rootFile = new File(root);
                    if (rootFile.isDirectory()) {
                        this.sourceFolders.add(rootFile);
                    }
                }
            }
        }

        public Set<File> getSourceFolders() {
            return this.sourceFolders;
        }
    }

    private class GatherManifestVisitor implements ProjectVisitor {
        private final Set<File> manifestFiles = new HashSet<>();

        @Override
        public void visit(final MavenProject project) {
            if (JavadocMojo.this.sourceTypes.contains(project.getPackaging())) {
                this.manifestFiles.add(new File(project.getBasedir(), "META-INF/MANIFEST.MF"));
            }
        }

        public Set<File> getManifestFiles() {
            return manifestFiles;
        }
    }

    private class GatherClasspathVisitor implements ProjectVisitor {
        private final Set<String> classPath = new HashSet<>();

        private BundleProject getBundleProject(final MavenProject project) throws MojoExecutionException {
            TychoProject projectType = projectTypes.get(project.getPackaging());
            if (!(projectType instanceof BundleProject)) {
                return null;
            }
            return (BundleProject) projectType;
        }

        @Override
        public void visit(final MavenProject project) throws MojoExecutionException {
            final BundleProject bp = getBundleProject(project);
            if (bp != null) {
                for (final ClasspathEntry cpe : bp.getClasspath(project)) {
                    cpe.getLocations().forEach(location -> this.classPath.add(location.getAbsolutePath()));
                }
            }
        }

        public Set<String> getClassPath() {
            return this.classPath;
        }
    }

    private void visitDeps(final Dependency dep, final ProjectVisitor visitor, final Set<String> scopes)
            throws MojoExecutionException {
        final MavenProject project = findProject(dep.getGroupId(), dep.getArtifactId());
        if (project == null) {
            getLog().info(String.format("Did not find project %s in reactor", dep));
            return;
        }

        getLog().debug("Adding sources from: " + project);

        visitor.visit(project);

        getLog().debug("Scanning dependencies: " + project.getDependencies().size());
        visitProjects(project.getDependencies(), scopes, visitor);

        getLog().debug("Done processing: " + project);
    }

    private MavenProject findProject(final String groupId, final String artifactId) {
        getLog().debug(String.format("findProject - groupId: %s, artifactId: %s", groupId, artifactId));

        for (final MavenProject p : this.reactorProjects) {
            if (!p.getGroupId().equals(groupId)) {
                continue;
            }
            if (!p.getArtifactId().equals(artifactId)) {
                continue;
            }
            return p;
        }
        return null;
    }
}
