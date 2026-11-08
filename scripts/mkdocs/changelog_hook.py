import re
from dataclasses import dataclass
from pathlib import Path

NOTES_DIRECTORY = Path(".github") / "release-notes"
NOTES_FILE_NAME = re.compile(r"^(\d+)\.(\d+)\.(\d+)(\.es)?\.md$")
HEADING = re.compile(r"^(#{1,6})(\s.*)?$")
FENCE = re.compile(r"^\s*(`{3,}|~{3,})")
DEEPEST_HEADING = 6
NOTES_TOP_HEADING = 3

PAGE_FILES = {"en": "changelog.md", "es": "changelog.es.md"}

TEXTS = {
    "en": {
        "title": "Changelog",
        "intro": "What each release brings, newest first. Versions published before these notes existed are in [all releases on GitHub]({releases}).",
        "link": "View on GitHub",
        "empty": "No release has notes yet.",
    },
    "es": {
        "title": "Historial de cambios",
        "intro": "Lo que trae cada versión, de la más nueva a la más antigua. Las versiones publicadas antes de que existieran estas notas están en [todas las versiones en GitHub]({releases}).",
        "link": "Ver en GitHub",
        "empty": "Ninguna versión tiene notas todavía.",
    },
}


@dataclass(frozen=True)
class Release:
    version: str
    english: str
    spanish: str | None

    def notes(self, locale):
        if locale == "es" and self.spanish is not None:
            return self.spanish
        return self.english


def collect_releases(notes_by_file_name):
    english = {}
    spanish = {}
    for file_name, content in notes_by_file_name.items():
        match = NOTES_FILE_NAME.match(file_name)
        if match is None:
            continue
        version = tuple(int(part) for part in match.group(1, 2, 3))
        target = spanish if match.group(4) else english
        target[version] = content
    return [
        Release(".".join(map(str, version)), english[version], spanish.get(version))
        for version in sorted(english, reverse=True)
    ]


def shift_headings(markdown, top_level):
    lines = markdown.split("\n")
    heading_lines = _heading_lines(lines)
    if not heading_lines:
        return markdown
    shallowest = min(len(HEADING.match(lines[index]).group(1)) for index in heading_lines)
    shift = top_level - shallowest
    for index in heading_lines:
        hashes, rest = HEADING.match(lines[index]).group(1, 2)
        level = min(len(hashes) + shift, DEEPEST_HEADING)
        lines[index] = "#" * level + (rest or "")
    return "\n".join(lines)


def _heading_lines(lines):
    found = []
    open_fence = None
    for index, line in enumerate(lines):
        fence = FENCE.match(line)
        if fence is not None:
            marker = fence.group(1)
            if open_fence is None:
                open_fence = marker
            elif marker[0] == open_fence[0] and len(marker) >= len(open_fence):
                open_fence = None
            continue
        if open_fence is None and HEADING.match(line):
            found.append(index)
    return found


def render_page(releases, locale, repo_url):
    texts = TEXTS.get(locale, TEXTS["en"])
    releases_url = repo_url.rstrip("/") + "/releases"
    sections = [f"# {texts['title']}", texts["intro"].format(releases=releases_url)]
    if not releases:
        sections.append(texts["empty"])
    for release in releases:
        sections.append(f"## {release.version}")
        sections.append(f"[{texts['link']}]({releases_url}/tag/v{release.version})")
        sections.append(shift_headings(release.notes(locale).strip(), NOTES_TOP_HEADING))
    return "\n\n".join(sections) + "\n"


def _notes_directory(config):
    return Path(config.config_file_path).parent / NOTES_DIRECTORY


def _read_notes(directory):
    if not directory.is_dir():
        return {}
    return {path.name: path.read_text(encoding="utf-8") for path in directory.iterdir() if path.is_file()}


def _write_if_changed(path, content):
    if path.is_file() and path.read_text(encoding="utf-8") == content:
        return
    path.write_text(content, encoding="utf-8")


def on_pre_build(config):
    releases = collect_releases(_read_notes(_notes_directory(config)))
    for locale, page_file in PAGE_FILES.items():
        content = render_page(releases, locale, config.repo_url)
        _write_if_changed(Path(config.docs_dir) / page_file, content)


def on_serve(server, config, builder):
    server.watch(str(_notes_directory(config)))
    return server
