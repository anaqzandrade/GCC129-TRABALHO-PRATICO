from app.keyword_search import KeywordIndex, stem
from app.knowledge import Chunk, chunk_id, load_sections, parse_markdown

from tests.conftest import KNOWLEDGE


def chunks_from_sections():
    """Um trecho por seção: basta para testar a busca sem o splitter do LangChain."""
    return [Chunk(chunk_id(s.file, i), s.file, s.title, s.source, s.heading, s.text)
            for i, s in enumerate(load_sections(KNOWLEDGE))]


def test_every_file_has_title_and_source():
    sections = load_sections(KNOWLEDGE)
    files = {s.file for s in sections}
    assert len(files) >= 4
    for section in sections:
        assert section.title and section.title != section.file
        assert section.source, section.file


def test_sections_follow_the_headings():
    sections = parse_markdown("x.md", "# Título\n\nFonte: teste\n\n## Primeira\n- a\n\n## Segunda\n- b\n")
    assert [(s.heading, s.text) for s in sections] == [("Primeira", "- a"), ("Segunda", "- b")]
    assert sections[0].source == "teste"


def test_chunk_ids_are_stable():
    assert chunk_id("a.md", 0) == chunk_id("a.md", 0) != chunk_id("a.md", 1)


def test_stem_matches_plural_and_singular():
    assert stem("laticinios") == stem("laticinio")
    assert stem("refeicoes") == stem("refeicao")


def test_keyword_search_finds_the_hot_holding_rule():
    index = KeywordIndex(chunks_from_sections())
    best = index.search("Por quanto tempo posso manter uma refeição quente antes de doar?", k=2)
    assert any("6 horas" in chunk.text for chunk in best)


def test_keyword_search_finds_the_current_law():
    index = KeywordIndex(chunks_from_sections())
    best = index.search("A Lei 14.016 ainda vale? Quem responde se o alimento doado fizer mal?", k=3)
    assert any("15.224" in chunk.content for chunk in best)


def test_keyword_search_returns_nothing_for_unrelated_words():
    assert KeywordIndex(chunks_from_sections()).search("xyzw qwerty", k=3) == []
