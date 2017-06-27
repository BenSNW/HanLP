/*
 * <summary></summary>
 * <author>hankcs</author>
 * <email>me@hankcs.com</email>
 * <create-date>2015/5/7 18:48</create-date>
 *
 * <copyright file="AhoCorasickDoubleArrayTrieSegment.java">
 * Copyright (c) 2003-2015, hankcs. All Right Reserved, http://www.hankcs.com/
 * </copyright>
 */
package com.hankcs.hanlp.seg.Other;

import com.hankcs.hanlp.collection.AhoCorasick.AhoCorasickDoubleArrayTrie;
import com.hankcs.hanlp.corpus.io.IOUtil;
import com.hankcs.hanlp.corpus.tag.PosTag;
import com.hankcs.hanlp.dictionary.CoreDictionary;
import com.hankcs.hanlp.seg.DictionaryBasedSegment;
import com.hankcs.hanlp.seg.NShort.Path.AtomNode;
import com.hankcs.hanlp.seg.Segment;
import com.hankcs.hanlp.seg.common.Term;
import com.hankcs.hanlp.util.TextUtility;

import static com.hankcs.hanlp.util.Predefine.logger;

import java.io.IOException;
import java.util.*;

/**
 * 使用AhoCorasickDoubleArrayTrie实现的最长分词器<br>
 * 需要用户调用setTrie()提供一个AhoCorasickDoubleArrayTrie
 *
 * @author hankcs
 */
public class AhoCorasickDoubleArrayTrieSegment extends DictionaryBasedSegment
{
    AhoCorasickDoubleArrayTrie<CoreDictionary.PosTagInfo> trie;

    @Override
    protected List<Term> segSentence(char[] sentence)
    {
        if (trie == null)
        {
            logger.warning("还未加载任何词典");
            return Collections.emptyList();
        }
        final int[] wordNet = new int[sentence.length];
        Arrays.fill(wordNet, 1);
        final PosTag[] natureArray = config.posTagging ? new PosTag[sentence.length] : null;
        trie.parseText(sentence, (begin, end, value) -> {
            int length = end - begin;
            if (length > wordNet[begin])
            {
                wordNet[begin] = length;
                if (config.posTagging)
                {
                    natureArray[begin] = value.pos[0];
                }
            }
        });
        LinkedList<Term> termList = new LinkedList<Term>();
        if (config.posTagging)
        {
            for (int i = 0; i < natureArray.length; )
            {
                if (natureArray[i] == null)
                {
                    int j = i + 1;
                    for (; j < natureArray.length; ++j)
                    {
                        if (natureArray[j] != null) break;
                    }
                    List<AtomNode> atomNodeList = quickAtomSegment(sentence, i, j);
                    for (AtomNode atomNode : atomNodeList)
                    {
                        if (atomNode.sWord.length() >= wordNet[i])
                        {
                            wordNet[i] = atomNode.sWord.length();
                            natureArray[i] = atomNode.getNature();
                            i += wordNet[i];
                        }
                    }
                    i = j;
                }
                else
                {
                    ++i;
                }
            }
        }
        for (int i = 0; i < wordNet.length; )
        {
            Term term = new Term(new String(sentence, i, wordNet[i]), config.posTagging ? (natureArray[i] == null ? PosTag.nz : natureArray[i]) : null);
            term.offset = i;
            termList.add(term);
            i += wordNet[i];
        }
        return termList;
    }

    public AhoCorasickDoubleArrayTrieSegment()
    {
        super();
        config.useCustomDictionary = false;
        config.posTagging = true;
    }

    @Override
    public Segment enableCustomDictionary(boolean enable)
    {
        throw new UnsupportedOperationException("AhoCorasickDoubleArrayTrieSegment暂时不支持用户词典。");
    }

    public AhoCorasickDoubleArrayTrie<CoreDictionary.PosTagInfo> getTrie()
    {
        return trie;
    }

    public void setTrie(AhoCorasickDoubleArrayTrie<CoreDictionary.PosTagInfo> trie)
    {
        this.trie = trie;
    }

    public AhoCorasickDoubleArrayTrieSegment loadDictionary(String... pathArray)
    {
        trie = new AhoCorasickDoubleArrayTrie<CoreDictionary.PosTagInfo>();
        TreeMap<String, CoreDictionary.PosTagInfo> map = null;
        try
        {
            map = IOUtil.loadDictionary(pathArray);
        }
        catch (IOException e)
        {
            logger.warning("加载词典失败\n" + TextUtility.exceptionToString(e));
            return this;
        }
        if (map != null && !map.isEmpty())
        {
            trie.build(map);
        }

        return this;
    }
}
