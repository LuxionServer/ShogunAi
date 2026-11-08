import unittest

from changelog_hook import collect_releases, render_page, shift_headings

REPO_URL = "https://github.com/example/project"


class CollectReleasesTest(unittest.TestCase):
    def test_sorts_versions_numerically_newest_first(self):
        notes = {"0.9.5.md": "a", "0.10.0.md": "b", "0.9.10.md": "c"}

        versions = [release.version for release in collect_releases(notes)]

        self.assertEqual(versions, ["0.10.0", "0.9.10", "0.9.5"])

    def test_ignores_files_that_are_not_release_notes(self):
        notes = {"README.md": "x", "0.9.5.txt": "x", "v0.9.6.md": "x", "0.9.md": "x", "0.9.5.md": "a"}

        versions = [release.version for release in collect_releases(notes)]

        self.assertEqual(versions, ["0.9.5"])

    def test_spanish_notes_are_kept_with_their_release(self):
        notes = {"0.9.5.md": "english", "0.9.5.es.md": "spanish"}

        release = collect_releases(notes)[0]

        self.assertEqual(release.notes("en"), "english")
        self.assertEqual(release.notes("es"), "spanish")

    def test_spanish_falls_back_to_english_notes(self):
        release = collect_releases({"0.9.6.md": "english"})[0]

        self.assertEqual(release.notes("es"), "english")

    def test_release_with_only_spanish_notes_is_not_listed(self):
        notes = {"0.9.7.es.md": "spanish", "0.9.5.md": "english"}

        versions = [release.version for release in collect_releases(notes)]

        self.assertEqual(versions, ["0.9.5"])


class ShiftHeadingsTest(unittest.TestCase):
    def test_shallowest_heading_becomes_the_given_level(self):
        notes = "## What's new\n\n- item\n\n### Detail\n"

        self.assertEqual(shift_headings(notes, 3), "### What's new\n\n- item\n\n#### Detail\n")

    def test_top_level_headings_are_shifted_too(self):
        self.assertEqual(shift_headings("# Fixes\n- item\n", 3), "### Fixes\n- item\n")

    def test_headings_never_go_deeper_than_six(self):
        self.assertEqual(shift_headings("# A\n###### B\n", 3), "### A\n###### B\n")

    def test_notes_without_headings_are_unchanged(self):
        self.assertEqual(shift_headings("- item\n#hashtag\n", 3), "- item\n#hashtag\n")

    def test_lines_inside_code_fences_are_unchanged(self):
        notes = "## Fixes\n\n```bash\n# a comment\n```\n\n~~~\n## not a heading\n~~~\n"

        expected = "### Fixes\n\n```bash\n# a comment\n```\n\n~~~\n## not a heading\n~~~\n"
        self.assertEqual(shift_headings(notes, 3), expected)


class RenderPageTest(unittest.TestCase):
    def test_english_page_lists_releases_with_their_links(self):
        releases = collect_releases({"0.9.5.md": "## What's new\n\n- old\n", "0.10.0.md": "## Fixes\n\n- new\n"})

        page = render_page(releases, "en", REPO_URL)

        self.assertTrue(page.startswith("# Changelog\n"))
        self.assertIn(f"[all releases on GitHub]({REPO_URL}/releases)", page)
        self.assertIn(f"## 0.10.0\n\n[View on GitHub]({REPO_URL}/releases/tag/v0.10.0)\n\n### Fixes\n\n- new\n", page)
        self.assertIn(f"## 0.9.5\n\n[View on GitHub]({REPO_URL}/releases/tag/v0.9.5)\n\n### What's new\n\n- old\n", page)
        self.assertLess(page.index("## 0.10.0"), page.index("## 0.9.5"))

    def test_spanish_page_uses_spanish_texts_and_notes(self):
        releases = collect_releases({"0.9.5.md": "## What's new\n", "0.9.5.es.md": "## Novedades\n"})

        page = render_page(releases, "es", REPO_URL)

        self.assertTrue(page.startswith("# Historial de cambios\n"))
        self.assertIn(f"[todas las versiones en GitHub]({REPO_URL}/releases)", page)
        self.assertIn(f"[Ver en GitHub]({REPO_URL}/releases/tag/v0.9.5)\n\n### Novedades\n", page)
        self.assertNotIn("What's new", page)

    def test_trailing_slash_in_repository_url_is_ignored(self):
        page = render_page(collect_releases({"0.9.5.md": "- item"}), "en", REPO_URL + "/")

        self.assertIn(f"({REPO_URL}/releases/tag/v0.9.5)", page)

    def test_page_without_releases_says_so(self):
        english = render_page([], "en", REPO_URL)
        spanish = render_page([], "es", REPO_URL)

        self.assertIn("No release has notes yet.", english)
        self.assertNotIn("## ", english)
        self.assertIn("Ninguna versión tiene notas todavía.", spanish)

    def test_unknown_locale_uses_english_texts(self):
        page = render_page([], "fr", REPO_URL)

        self.assertTrue(page.startswith("# Changelog\n"))


if __name__ == "__main__":
    unittest.main()
