//usr/bin/env jshell --show-version --execution local "-J-Dclone=$@" "$0"; exit $?
import java.io.*;
import java.lang.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.stream.*;
System.out.println("Hello World");
System.out.println("Working in: " + System.getProperty("user.dir"));
final Path workDir = Paths.get(".").toAbsolutePath();

final boolean CI = System.getenv().containsKey("CI");
if (CI) System.out.println("CI environment detected.");

//split, normalize & dedup values
Set<String> params = Stream.of(System.getProperty("clone", "").trim().split("\\s+")).
    filter(Predicate.not(String::isBlank)).
    map(s -> s.toUpperCase(Locale.ROOT).replace('-', '_')).
    collect(Collectors.toUnmodifiableSet());
if (params.isEmpty()) {
    System.out.println("No repository specified.");
} else {
    System.out.println("Asked repositories (args): " + params.toString());
}

File cd(final String path) {
    System.out.println("$ cd " + path);
    return workDir.resolve(path).normalize().toFile();
}
void run(final File dir, final String ...command) throws IOException, InterruptedException {
    System.out.println("$[" + String.join(", ", command) + "] on " + dir.getPath());
    ProcessBuilder builder = new ProcessBuilder(command)
        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .directory(dir);
    builder.environment().put("GIT_TERMINAL_PROMPT", "0");
    final Process process = builder.start();
    builder = null;
    final int exitCode = process.waitFor(/*20, TimeUnit.SECONDS*/);
    System.out.println("** ExitValue: " + exitCode);
    if (exitCode != 0) System.exit(exitCode);
    //System.out.println();
}

// Need to have a static map because root package isn't normalized in projects...
final Map<String, String> basePackages = new HashMap<>();
basePackages.putAll(Map.of(
    "actions-server", "org.gridsuite.actions.server",
    "balances-adjustment-server", "org.gridsuite.balances.adjustment.server",
    "case-import-server", "org.gridsuite.caseimport.server",
    "case-validation-server", "org.gridsuite.casevalidation.server",
    "cgmes-boundary-server", "org.gridsuite.cgmes.boundary.server",
    "cgmes-gl-server", "org.gridsuite.cgmes.gl.server",
    "config-server", "org.gridsuite.config.server",
    "directory-server", "org.gridsuite.directory.server",
    "dynamic-mapping-server", "org.gridsuite.mapping.server",
    "dynamic-simulation-server", "org.gridsuite.ds.server"
));
basePackages.putAll(Map.of(
    "explore-server", "org.gridsuite.explore.server",
    "filter-server", "org.gridsuite.filter.server",
    "geo-data-server", "org.gridsuite.geodata.server",
    "loadflow-server", "org.gridsuite.loadflow.server",
    "merge-orchestrator-server", "org.gridsuite.merge.orchestrator.server",
    "network-map-server", "org.gridsuite.network.map",
    "network-modification-server", "org.gridsuite.modification.server",
    "odre-server", "org.gridsuite.odre.server",
    "report-server", "org.gridsuite.report.server",
    "security-analysis-server", "org.gridsuite.securityanalysis.server"
));
basePackages.putAll(Map.of(
    "sensitivity-analysis-server", "org.gridsuite.sensitivityanalysis.server",
    "shortcircuit-server", "org.gridsuite.shortcircuit.server",
    "study-server", "org.gridsuite.study.server",
    "timeseries-server", "org.gridsuite.timeseries.server",
    "user-admin-server", "org.gridsuite.useradmin.server",
    "user-identity-oidc-replication-server", "org.gridsuite.useridentity.oidcreplication.server",
    "voltage-init-server", "org.gridsuite.voltageinit.server"
));

//TODO add powsybl servers
//TODO failed if arg not existing instead of ignoring it silently
final List<String> reposTodo = Stream.of("ACTIONS", "BALANCES_ADJUSTMENT",
    "CASE_IMPORT", "CASE_VALIDATION", "CGMES_BOUNDARY", "CGMES_GL", "CONFIG", "DIRECTORY", "DYNAMIC_MAPPING", "DYNAMIC_SIMULATION",
    "EXPLORE", "FILTER", "GEO_DATA", "LOADFLOW", "MERGE_ORCHESTRATOR", "NETWORK_MAP", "NETWORK_MODIFICATION", "ODRE", "REPORT",
    "SECURITY_ANALYSIS", "SENSITIVITY_ANALYSIS", "SHORTCIRCUIT", "STUDY", "TIMESERIES", "USER_ADMIN"/*, "USER_IDENTITY_OIDC_REPLICATION"*/, "VOLTAGE_INIT").
    /* $CI | params    | result
     * ----+-----------+-------
     *  ✓  | ∅         | ✗ (already taken care)
     *  ✓  | match     | ✓
     *  ✓  | not match | ✗
     *  ✗  | ∅         | ✓
     *  ✗  | match     | ✓
     *  ✗  | not match | ✗
     */
    filter(opt -> (!CI && params.isEmpty()) || params.contains(opt)).
    map(str -> str.toLowerCase(Locale.ROOT).replace('_', '-') + "-server").
    toList();
params = null;

/* if env var CI=true, then only specified repositories are cloned (done for parallelize on CI)
 * else if none specified (in args), then clone all repos
 * else clone specified repositories.
 */
if (CI && reposTodo.isEmpty()) {
    System.err.println("We need a repository specified when in a CI environment");
    System.exit(-1);
} else if (!CI && reposTodo.isEmpty()) {
    System.out.println("All repositories selected.");
}

final Path dist = Paths.get(".", "dist");
// /set feedback silent
if (Files.exists(dist)) {
    try (final Stream<Path> paths = Files.walk(dist)) {
        paths.sequential().sorted(Comparator.reverseOrder()).forEach(p -> {try{Files.delete(p);}catch(final IOException err){throw new RuntimeException(err);}});
    }
}
Files.createDirectory(dist);
// /set feedback normal

System.out.println("Repositories to clone: " + reposTodo.toString());
for(final String repo : reposTodo) {
    System.out.println();
    System.out.println("Starting process for " + repo);
    final File wDir = cd(repo);
    if (!Files.isDirectory(Paths.get(".", repo))) {
        /* We clone the repo if not existing */
        run(workDir.toFile(), "git", "--no-pager", "clone", "--depth", "1", "https://github.com/gridsuite/" + repo + ".git");
    } else {
        /* We must update the repo (will discard all change and to "main" branch */
        run(wDir, "git", "--no-pager", "reset", "--hard", "HEAD"); //reset tracked files
        run(wDir, "git", "--no-pager", "clean", "--force", "-d" , "-x"); //delete untracked files
        run(wDir, "git", "--no-pager", "checkout", "--no-guess", "--track", "--force", "-B", "main"); //go to main branch in case not already there
        run(wDir, "git", "--no-pager", "pull");
        //run(wDir, "git", "--no-pager", "switch", "--track", "--no-guess", "--discard-changes", "--force-create", "main", "origin/main");
    }

    /* We get the swagger with springdoc through a false test to run directly in a functional environment */
    //TODO it would be more simple if we have springdoc-openapi-maven-plugin usable...
    // We look if springdoc is present
    String tmpStr = Files.readString(wDir.toPath().resolve("pom.xml"));
    final boolean isWebMvc = tmpStr.contains("springdoc-openapi-starter-webmvc");
    final boolean isWebflux = tmpStr.contains("springdoc-openapi-starter-webflux");
    tmpStr = null;
    if (!isWebMvc && !isWebflux) {
        System.err.println("SpringDoc not detected!");
        System.exit(-2);
    }
    if (isWebMvc == isWebflux) {
        System.err.println("Don't support webmvc & webflux simultaneously.");
        System.exit(-3);
    }
    // Search the root folder name
    final String pkgFull = basePackages.get(repo);
    System.out.println("Package: \"" + pkgFull + "\"");
    final Path pkgPath = Paths.get("java", pkgFull.split("\\."));
    // Now we copy the test file in the correct folder
    System.out.println("Spring/SpringDoc web \"mode\": " + (isWebflux ? "Webflux" : "WebMvc"));
    final Path testFileDst = wDir.toPath().resolve(Paths.get("src", "test")).resolve(pkgPath).resolve("SwaggerDumpTest.java");
    System.out.println("Writing in " + testFileDst.toString());
    Files.writeString(testFileDst, "package " + pkgFull + ";" + System.lineSeparator() + Files.readString(Paths.get(".", isWebflux ? "SwaggerFluxDumpTest.java" : "SwaggerMvcDumpTest.java")), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    // We can finally run the test
    //$ mvn spring-boot:help -Ddetail=true -Dgoal=<goal-name>
    run(wDir, "mvn", "-Dcheckstyle.skip=true", "-Dtest=" + pkgFull + ".SwaggerDumpTest", "test");
    /* Lets keep the artifact in build directory */
    Files.copy(wDir.toPath().resolve(Paths.get("target", "openapi.json")), dist.resolve("openapi-" + repo.replace("-server", "") + ".json"), StandardCopyOption.REPLACE_EXISTING);
}

/exit
/* TODO: look for swagger alternative for events servers
https://github.com/gridsuite/config-notification-server
https://github.com/gridsuite/directory-notification-server
https://github.com/gridsuite/merge-notification-server
https://github.com/gridsuite/study-notification-server
*/
