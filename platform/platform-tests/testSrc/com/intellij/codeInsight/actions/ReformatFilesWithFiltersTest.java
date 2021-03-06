
package com.intellij.codeInsight.actions;

import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.MutablePicoContainer;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;

import static com.intellij.psi.search.GlobalSearchScopesCore.directoryScope;

public class ReformatFilesWithFiltersTest extends LightPlatformTestCase {
  private static final String TEMP_DIR_NAME = "dir";
  private PsiDirectory myWorkingDirectory;

  private MockCodeStyleManager myMockCodeStyleManager;
  private MockPlainTextFormattingModelBuilder myMockPlainTextFormattingModelBuilder;

  private CodeStyleManager myRealCodeStyleManger;

  @Override
  public void setUp() throws Exception {
    PlatformTestCase.initPlatformLangPrefix();
    super.setUp();
    myWorkingDirectory = TestFileStructure.createDirectory(getProject(), getSourceRoot(), TEMP_DIR_NAME);

    myRealCodeStyleManger = CodeStyleManager.getInstance(getProject());
    myMockCodeStyleManager = new MockCodeStyleManager();
    registerCodeStyleManager(myMockCodeStyleManager);

    myMockPlainTextFormattingModelBuilder = new MockPlainTextFormattingModelBuilder();
    LanguageFormatting.INSTANCE.addExplicitExtension(PlainTextLanguage.INSTANCE, myMockPlainTextFormattingModelBuilder);
  }

  @Override
  public void tearDown() throws Exception {
    registerCodeStyleManager(myRealCodeStyleManger);
    LanguageFormatting.INSTANCE.removeExplicitExtension(PlainTextLanguage.INSTANCE, myMockPlainTextFormattingModelBuilder);

    TestFileStructure.delete(myWorkingDirectory.getVirtualFile());
    super.tearDown();
  }

  private static void registerCodeStyleManager(@NotNull CodeStyleManager manager) {
    String componentKey = CodeStyleManager.class.getName();
    MutablePicoContainer container = (MutablePicoContainer)getProject().getPicoContainer();
    container.unregisterComponent(componentKey);
    container.registerComponentInstance(componentKey, manager);
  }

  public void testReformatWithoutMask() throws IOException {
    TestFileStructure fileTree = new TestFileStructure(getModule(), myWorkingDirectory);

    PsiFile java1 = fileTree.addTestFile("Test.java", "empty content");
    PsiFile java2 = fileTree.addTestFile("Pair.java", "empty content");
    PsiFile java3 = fileTree.addTestFile("Pair2.java", "empty content");

    PsiFile php = fileTree.addTestFile("Test.php", "empty content");
    PsiFile js = fileTree.addTestFile("Test.js", "empty content");

    reformatDirectoryWithFileMask(myWorkingDirectory, null);
    assertWasFormatted(java1, java2, java3, php, js);
  }

  public void testFormatByOnlyOneMask() throws IOException {
    TestFileStructure fileTree = new TestFileStructure(getModule(), myWorkingDirectory);

    PsiFile java1 = fileTree.addTestFile("Test.java", "empty content");
    PsiFile java2 = fileTree.addTestFile("Pair.java", "empty content");
    PsiFile java3 = fileTree.addTestFile("Pair2.java", "empty content");

    PsiFile php = fileTree.addTestFile("Test.php", "empty content");
    PsiFile js = fileTree.addTestFile("Test.js", "empty content");

    reformatDirectoryWithFileMask(myWorkingDirectory, "*.java");
    assertWasFormatted(java1, java2, java3);
    assertWasNotFormatted(php, js);

    reformatDirectoryWithFileMask(myWorkingDirectory, "*.js");
    assertWasFormatted(js);
    assertWasNotFormatted(java1, java2, java3, php);

  }

  public void testFormatByMultiMask() throws IOException {
    TestFileStructure fileTree = new TestFileStructure(getModule(), myWorkingDirectory);

    PsiFile java1 = fileTree.addTestFile("Test.java", "empty content");
    PsiFile java2 = fileTree.addTestFile("Pair.java", "empty content");
    PsiFile java3 = fileTree.addTestFile("Pair2.java", "empty content");

    PsiFile php1 = fileTree.addTestFile("Test.php", "empty content");
    PsiFile php2 = fileTree.addTestFile("Test2.php", "empty content");

    PsiFile js1 = fileTree.addTestFile("Test1.js", "empty content");
    PsiFile js2 = fileTree.addTestFile("Test2.js", "empty content");
    PsiFile js3 = fileTree.addTestFile("Test3.js", "empty content");

    PsiFile py1 = fileTree.addTestFile("Test1.py", "empty content");
    PsiFile py2 = fileTree.addTestFile("Test2.py", "empty content");
    PsiFile py3 = fileTree.addTestFile("Test3.py", "empty content");

    reformatDirectoryWithFileMask(myWorkingDirectory, "*.js, *.java");
    assertWasFormatted(js1, js2, js3, java1, java2, java3);
    assertWasNotFormatted(php1, php2, py1, py2, py3);

    reformatDirectoryWithFileMask(myWorkingDirectory, "*.php, *.js");
    assertWasFormatted(js1, js2, js3, php1, php2);
    assertWasNotFormatted(java1, java2, java3, py1, py2, py3);

    reformatDirectoryWithFileMask(myWorkingDirectory, "*.js, *.php, *.java");
    assertWasFormatted(js1, js2, js3, php1, php2, java1, java2, java3);
    assertWasNotFormatted(py1, py2, py3);

    reformatDirectoryWithFileMask(myWorkingDirectory, "*.js, *.php, *.java, *.py");
    assertWasFormatted(js1, js2, js3, php1, php2, java1, java2, java3, py1, py2, py3);

    reformatDirectoryWithFileMask(myWorkingDirectory, "*.jsp, *.dart");
    assertWasNotFormatted(js1, js2, js3, php1, php2, java1, java2, java3, py1, py2, py3);
  }

  public void testDirectoryScope() throws IOException {
    TestFileStructure fileTree = new TestFileStructure(getModule(), myWorkingDirectory);
    PsiFile java1 = fileTree.addTestFile("Test1.java", "empty content");
    PsiFile php1 = fileTree.addTestFile("Pair1.php", "empty content");
    PsiFile js1 = fileTree.addTestFile("Pair1.js", "empty content");

    PsiDirectory outer = fileTree.createDirectoryAndMakeItCurrent("toFormat");
    PsiFile java2 = fileTree.addTestFile("Test2.java", "empty content");
    PsiFile php2 = fileTree.addTestFile("Pair2.php", "empty content");
    PsiFile js2 = fileTree.addTestFile("Pair2.js", "empty content");

    PsiDirectory inner = fileTree.createDirectoryAndMakeItCurrent("toFormat");
    PsiFile java3 = fileTree.addTestFile("Test3.java", "empty content");
    PsiFile php3 = fileTree.addTestFile("Pair3.php", "empty content");
    PsiFile js3 = fileTree.addTestFile("Pair3.js", "empty content");


    reformatDirectoryWithScopeFilter(myWorkingDirectory, directoryScope(outer, true));
    assertWasFormatted(java2, php2, js2, java3, php3, js3);
    assertWasNotFormatted(java1, php1, js1);


    reformatDirectoryWithScopeFilter(myWorkingDirectory, directoryScope(outer, false));
    assertWasFormatted(java2, php2, js2);
    assertWasNotFormatted(java1, php1, js1, java3, php3, js3);


    reformatDirectoryWithScopeFilter(myWorkingDirectory, directoryScope(inner, true));
    assertWasFormatted(java3, php3, js3);
    assertWasNotFormatted(java1, php1, js1, java2, php2, js2);

    reformatDirectoryWithScopeFilter(myWorkingDirectory, directoryScope(myWorkingDirectory, false).union(directoryScope(inner, false)));
    assertWasFormatted(java3, php3, js3, java1, php1, js1);
    assertWasNotFormatted(java2, php2, js2);
  }

  public void testDirectoryScopeWithMask() throws IOException {
    TestFileStructure fileTree = new TestFileStructure(getModule(), myWorkingDirectory);
    PsiFile java1 = fileTree.addTestFile("Test1.java", "empty content");
    PsiFile php1 = fileTree.addTestFile("Pair1.php", "empty content");
    PsiFile js1 = fileTree.addTestFile("Pair1.js", "empty content");

    PsiDirectory outer = fileTree.createDirectoryAndMakeItCurrent("toFormat");
    PsiFile java2 = fileTree.addTestFile("Test2.java", "empty content");
    PsiFile php2 = fileTree.addTestFile("Pair2.php", "empty content");
    PsiFile js2 = fileTree.addTestFile("Pair2.js", "empty content");

    PsiDirectory inner = fileTree.createDirectoryAndMakeItCurrent("toFormat");
    PsiFile java3 = fileTree.addTestFile("Test3.java", "empty content");
    PsiFile php3 = fileTree.addTestFile("Pair3.php", "empty content");
    PsiFile js3 = fileTree.addTestFile("Pair3.js", "empty content");


    reformatDirectory(myWorkingDirectory, "*.js", directoryScope(outer, true));
    assertWasFormatted(js2, js3);
    assertWasNotFormatted(java1, php1, js1, java2, php2, java3, php3);

    reformatDirectory(myWorkingDirectory, "*.js", directoryScope(myWorkingDirectory, false));
    assertWasFormatted(js1);
    assertWasNotFormatted(js2, js3, java1, php1, java2, php2, java3, php3);

    reformatDirectory(myWorkingDirectory, "*.java, *.php", directoryScope(myWorkingDirectory, false).union(directoryScope(inner, false)));
    assertWasFormatted(java1, php1, java3, php3);
    assertWasNotFormatted(java2, php2, js1, js2, js3);
  }

  public void testIDEA126830() throws IOException {
    TestFileStructure fileTree = new TestFileStructure(getModule(), myWorkingDirectory);

    fileTree.createDirectoryAndMakeItCurrent("src");
    PsiFile java2 = fileTree.addTestFile("Test2.java", "empty content");
    PsiFile php2 = fileTree.addTestFile("Pair2.php", "empty content");
    PsiFile js2 = fileTree.addTestFile("Pair2.js", "empty content");

    PsiDirectory test = fileTree.createDirectoryAndMakeItCurrent("test");
    PsiFile testJava1 = fileTree.addTestFile("testJava1.java", "empty content");
    PsiFile testPhp1 = fileTree.addTestFile("testPhp1.php", "empty content");
    PsiFile testJs1 = fileTree.addTestFile("testJs1.js", "empty content");

    GlobalSearchScope testScope = directoryScope(test, true);

    Logger logger = Logger.getInstance(getClass());
    logFiles(logger, "Previously formatted files: ", myMockCodeStyleManager.getFormattedFiles());

    reformatWithRearrange(myWorkingDirectory, testScope);
    logFiles(logger, "Currently formatted files: ", myMockCodeStyleManager.getFormattedFiles());
    logFiles(logger, "Should be formatted", ContainerUtil.newArrayList(testJava1, testPhp1, testJs1));

    assertWasFormatted(testJava1, testPhp1, testJs1);
    assertWasNotFormatted(java2, php2, js2);

    reformatAndOptimize(myWorkingDirectory, testScope);
    assertWasFormatted(testJava1, testPhp1, testJs1);
    assertWasNotFormatted(java2, php2, js2);
  }

  private void logFiles(Logger log, String message, Collection<PsiFile> files) {
    StringBuilder builder;
    builder = new StringBuilder();
    builder.append(message).append('\n');
    for (PsiFile file : files) {
      builder.append(file).append('\n');
    }
    log.info(builder.toString());
  }

  public void assertWasFormatted(PsiFile... files) {
    final Set<PsiFile> formattedFiles = myMockCodeStyleManager.getFormattedFiles();
    for (PsiFile file : files) {
      assertTrue(file.getName() + " should be formatted", formattedFiles.contains(file));
    }
  }

  public void assertWasNotFormatted(PsiFile... files) {
    final Set<PsiFile> formattedFiles = myMockCodeStyleManager.getFormattedFiles();
    for (PsiFile file : files) {
      assertTrue(file.getName() + " should not be formatted", !formattedFiles.contains(file));
    }
  }

  public void reformatDirectoryWithFileMask(@NotNull PsiDirectory directory, @Nullable String mask) {
    reformatDirectory(directory, mask, null);
  }

  public void reformatDirectoryWithScopeFilter(@NotNull PsiDirectory directory, @Nullable SearchScope scope) {
    reformatDirectory(directory, null, scope);
  }

  public void reformatWithRearrange(@NotNull PsiDirectory directory, @Nullable SearchScope scope) {
    myMockCodeStyleManager.clearFormattedFiles();
    AbstractLayoutCodeProcessor processor = new ReformatCodeProcessor(getProject(), directory, true, false);
    ReformatCodeAction.registerScopeFilter(processor, scope);

    processor = new RearrangeCodeProcessor(processor, null);
    processor.run();
  }

  private void reformatAndOptimize(@NotNull PsiDirectory workingDirectory, @NotNull GlobalSearchScope scope) {
    myMockCodeStyleManager.clearFormattedFiles();
    AbstractLayoutCodeProcessor processor = new ReformatCodeProcessor(getProject(), workingDirectory, true, false);
    ReformatCodeAction.registerScopeFilter(processor, scope);

    processor = new OptimizeImportsProcessor(processor);
    processor.run();
  }

  public void reformatDirectory(@NotNull PsiDirectory directory, @Nullable String mask, @Nullable SearchScope scope) {
    myMockCodeStyleManager.clearFormattedFiles();

    ReformatCodeProcessor processor = new ReformatCodeProcessor(getProject(), directory, true, false);
    ReformatCodeAction.registerFileMaskFilter(processor, mask);
    ReformatCodeAction.registerScopeFilter(processor, scope);

    processor.run();
  }
}
