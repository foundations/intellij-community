package com.jetbrains.python.packaging;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.webcore.packaging.PackageVersionComparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author vlan
 */
public class PyRequirement {
  private static final Pattern NAME = Pattern.compile("\\s*(\\w(\\w|[-.])*)\\s*(.*)");
  private static final Pattern VERSION_SPEC = Pattern.compile("\\s*(<=?|>=?|==|!=)\\s*((\\w|[-.])+)");
  private static final Pattern EDITABLE_EGG = Pattern.compile("\\s*(-e)?\\s*([^#]*)(#egg=(.*))?");
  private static final Pattern RECURSIVE_REQUIREMENT = Pattern.compile("\\s*-r\\s+(.*)");
  private static final Pattern VCS_PATH = Pattern.compile(".*/([^/]+)/?");

  public enum Relation {
    LT("<"),
    LTE("<="),
    GT(">"),
    GTE(">="),
    EQ("=="),
    NE("!=");

    @NotNull private final String myValue;

    Relation(@NotNull String value) {
      myValue = value;
    }

    @NotNull
    @Override
    public String toString() {
      return myValue;
    }

    @Nullable
    public static Relation fromString(@NotNull String value) {
      for (Relation relation : Relation.values()) {
        if (relation.myValue.equals(value)) {
          return relation;
        }
      }
      return null;
    }

    public boolean isSuccessful(int comparisonResult) {
      switch (this) {
        case LT:
          return comparisonResult < 0;
        case LTE:
          return comparisonResult <= 0;
        case GT:
          return comparisonResult > 0;
        case GTE:
          return comparisonResult >= 0;
        case EQ:
          return comparisonResult == 0;
        case NE:
          return comparisonResult != 0;
      }
      return false;
    }
  }

  public static class VersionSpec {
    @NotNull private final Relation myRelation;
    @NotNull private final String myVersion;

    public VersionSpec(@NotNull Relation relation, @NotNull String version) {
      myRelation = relation;
      myVersion = version;
    }

    @Override
    public String toString() {
      return myRelation + myVersion;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      VersionSpec spec = (VersionSpec)o;
      if (myRelation != spec.myRelation) return false;
      if (!myVersion.equals(spec.myVersion)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = myRelation.hashCode();
      result = 31 * result + myVersion.hashCode();
      return result;
    }

    @NotNull
    public Relation getRelation() {
      return myRelation;
    }

    @NotNull
    public String getVersion() {
      return myVersion;
    }
  }

  @NotNull private final String myName;
  @NotNull private final List<VersionSpec> myVersionSpecs;
  @Nullable private final String myURL;
  private final boolean myEditable;

  public PyRequirement(@NotNull String name) {
    this(name, Collections.<VersionSpec>emptyList());
  }

  public PyRequirement(@NotNull String name, @NotNull String version) {
    this(name, Collections.singletonList(new VersionSpec(Relation.EQ, version)));
  }

  public PyRequirement(@NotNull String name, @NotNull List<VersionSpec> versionSpecs) {
    myName = name;
    myVersionSpecs = versionSpecs;
    myURL = null;
    myEditable = false;
  }

  public PyRequirement(@NotNull String name, @Nullable String version, @NotNull String url, boolean editable) {
    myName = name;
    if (version != null) {
      myVersionSpecs = Collections.singletonList(new VersionSpec(Relation.EQ, version));
    }
    else {
      myVersionSpecs = Collections.emptyList();
    }
    myURL = url;
    myEditable = editable;
  }

  @NotNull
  @Override
  public String toString() {
    return myName + StringUtil.join(myVersionSpecs,
                                    new Function<VersionSpec, String>() {
                                      @Override
                                      public String fun(VersionSpec spec) {
                                        return spec.toString();
                                      }
                                    },
                                    ",");
  }

  @NotNull
  public List<String> toOptions() {
    final List<String> results = new ArrayList<String>();
    if (myEditable) {
      results.add("-e");
    }
    if (myURL != null) {
      final int size = myVersionSpecs.size();
      assert size <= 1;
      final String urlAndName = myURL + "#egg=" + myName;
      if (size == 0) {
        results.add(urlAndName);
      }
      else {
        final VersionSpec versionSpec = myVersionSpecs.get(0);
        assert versionSpec.getRelation() == Relation.EQ;
        results.add(urlAndName + "-" + versionSpec.getVersion());
      }
      return results;
    }
    else {
      results.add(toString());
    }
    return results;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PyRequirement that = (PyRequirement)o;

    if (myEditable != that.myEditable) return false;
    if (!myName.equals(that.myName)) return false;
    if (myURL != null ? !myURL.equals(that.myURL) : that.myURL != null) return false;
    if (!myVersionSpecs.equals(that.myVersionSpecs)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myName.hashCode();
    result = 31 * result + myVersionSpecs.hashCode();
    result = 31 * result + (myURL != null ? myURL.hashCode() : 0);
    result = 31 * result + (myEditable ? 1 : 0);
    return result;
  }

  @Nullable
  public PyPackage match(@NotNull List<PyPackage> packages) {
    for (PyPackage pkg : packages) {
      if (normalizeName(myName).equalsIgnoreCase(pkg.getName())) {
        for (VersionSpec spec : myVersionSpecs) {
          final int cmp = PackageVersionComparator.VERSION_COMPARATOR.compare(pkg.getVersion(), spec.getVersion());
          final Relation relation = spec.getRelation();
          if (!relation.isSuccessful(cmp)) {
            return null;
          }
        }
        return pkg;
      }
    }
    return null;
  }

  @Nullable
  public static PyRequirement fromString(@NotNull String line) {
    // TODO: Extras, multi-line requirements '\'
    final PyRequirement editableEgg = parseEditableEgg(line);
    if (editableEgg != null) {
      return editableEgg;
    }
    final Matcher nameMatcher = NAME.matcher(line);
    if (!nameMatcher.matches()) {
      return null;
    }
    final String name = nameMatcher.group(1);
    final String rest = nameMatcher.group(3);
    final List<VersionSpec> versionSpecs = new ArrayList<VersionSpec>();
    if (!rest.trim().isEmpty()) {
      final Matcher versionSpecMatcher = VERSION_SPEC.matcher(rest);
      while (versionSpecMatcher.find()) {
        final String rel = versionSpecMatcher.group(1);
        final String version = versionSpecMatcher.group(2);
        final Relation relation = Relation.fromString(rel);
        if (relation == null) {
          return null;
        }
        versionSpecs.add(new VersionSpec(relation, version));
      }
    }
    return new PyRequirement(name, versionSpecs);
  }

  @NotNull
  public static List<PyRequirement> parse(@NotNull String s) {
    return parse(s, null, new HashSet<VirtualFile>());
  }

  @NotNull
  public static List<PyRequirement> parse(@NotNull VirtualFile file) {
    return parse(file, new HashSet<VirtualFile>());
  }

  @NotNull
  public static List<PyRequirement> parse(@NotNull VirtualFile file, @NotNull Set<VirtualFile> visited) {
    if (!visited.contains(file)) {
      visited.add(file);
      final Document document = FileDocumentManager.getInstance().getDocument(file);
      if (document != null) {
        return parse(document.getText(), file, visited);
      }
    }
    return Collections.emptyList();
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  private static List<PyRequirement> parse(@NotNull String s, @Nullable VirtualFile anchor, @NotNull Set<VirtualFile> visited) {
    final List<PyRequirement> result = new ArrayList<PyRequirement>();
    for (String line : StringUtil.splitByLines(s)) {
      final String trimmed = line.trim();
      if (!trimmed.isEmpty()) {
        final PyRequirement req = fromString(line);
        if (req != null) {
          result.add(req);
        }
        else if (anchor != null) {
          result.addAll(parseRecursiveRequirement(trimmed, anchor, visited));
        }
      }
    }
    return result;
  }

  @NotNull
  private static List<PyRequirement> parseRecursiveRequirement(@NotNull String line, @NotNull VirtualFile anchor,
                                                               @NotNull Set<VirtualFile> visited) {
    final Matcher matcher = RECURSIVE_REQUIREMENT.matcher(line);
    if (matcher.matches()) {
      final String fileName = FileUtil.toSystemIndependentName(matcher.group(1));
      final VirtualFile dir = anchor.getParent();
      if (dir != null) {
        VirtualFile file = dir.findFileByRelativePath(fileName);
        if (file == null) {
          file = LocalFileSystem.getInstance().findFileByPath(fileName);
        }
        if (file != null) {
          return parse(file, visited);
        }

      }
    }
    return Collections.emptyList();
  }

  @Nullable
  private static PyRequirement parseEditableEgg(@NotNull String line) {
    final Matcher editableEggMatcher = EDITABLE_EGG.matcher(line);
    if (!editableEggMatcher.matches()) {
      return null;
    }
    final boolean editable = editableEggMatcher.group(1) != null;
    final String url = editableEggMatcher.group(2);
    String egg = editableEggMatcher.group(4);
    if (url == null) {
      return null;
    }
    if (egg == null) {
      try {
        final URI uri = new URI(url);
        if (uri.getScheme() != null) {
          String path = uri.getPath();
          if (path != null) {
            final String[] split = path.split("@", 2);
            path = split[0];
            final Matcher vcsPathMatcher = VCS_PATH.matcher(path);
            if (!vcsPathMatcher.matches()) {
              return null;
            }
            egg = vcsPathMatcher.group(1);
            final String gitSuffix = ".git";
            if (egg.endsWith(gitSuffix)) {
              egg = egg.substring(0, egg.length() - gitSuffix.length());
            }
          }
        }
      }
      catch (URISyntaxException e) {
        return null;
      }
    }
    if (egg == null) {
      return null;
    }
    boolean isName = true;
    final List<String> nameParts = new ArrayList<String>();
    final List<String> versionParts = new ArrayList<String>();
    for (String part : StringUtil.split(egg, "-")) {
      if (part.matches("[0-9].*") || "dev".equals(part)) {
        isName = false;
      }
      if (isName) {
        nameParts.add(part);
      }
      else {
        versionParts.add(part);
      }
    }
    final String name = normalizeName(StringUtil.join(nameParts, "-"));
    final String version = !versionParts.isEmpty() ? normalizeVersion(StringUtil.join(versionParts, "-")) : null;
    return new PyRequirement(name, version, url, editable);
  }

  @NotNull
  private static String normalizeName(@NotNull String s) {
    return s.replace("_", "-");
  }

  @NotNull
  private static String normalizeVersion(@NotNull String s) {
    return s.replace("_", "-").replaceAll("-?py[0-9\\.]+", "");
  }
}
