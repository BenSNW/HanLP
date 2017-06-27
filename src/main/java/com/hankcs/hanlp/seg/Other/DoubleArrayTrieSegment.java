/*
 * <summary></summary>
 * <author>He Han</author>
 * <email>hankcs.cn@gmail.com</email>
 * <create-date>2014/12/23 21:34</create-date>
 *
 * <copyright file="AhoCorasickSegment.java" company="上海林原信息科技有限公司">
 * Copyright (c) 2003-2014, 上海林原信息科技有限公司. All Right Reserved, http://www.linrunsoft.com/
 * This source is subject to the LinrunSpace License. Please contact 上海林原信息科技有限公司 to get more information.
 * </copyright>
 */
package com.hankcs.hanlp.seg.Other;

import com.hankcs.hanlp.collection.trie.DoubleArrayTrie;
import com.hankcs.hanlp.corpus.tag.PosTag;
import com.hankcs.hanlp.dictionary.CoreDictionary;
import com.hankcs.hanlp.dictionary.CustomDictionary;
import com.hankcs.hanlp.seg.DictionaryBasedSegment;
import com.hankcs.hanlp.seg.NShort.Path.AtomNode;
import com.hankcs.hanlp.seg.common.Term;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * 使用DoubleArrayTrie实现的最长分词器
 *
 * @author hankcs
 */
public class DoubleArrayTrieSegment extends DictionaryBasedSegment
{
    @Override
    protected List<Term> segSentence(char[] sentence)
    {
        char[] charArray = sentence;
        final int[] wordNet = new int[charArray.length];
        Arrays.fill(wordNet, 1);
        final PosTag[] natureArray = config.posTagging ? new PosTag[charArray.length] : null;
        DoubleArrayTrie<CoreDictionary.PosTagInfo>.Searcher searcher = CoreDictionary.trie.getSearcher(sentence, 0);
        while (searcher.next())
        {
            int length = searcher.length;
            if (length > wordNet[searcher.begin])
            {
                wordNet[searcher.begin] = length;
                if (config.posTagging)
                {
                    natureArray[searcher.begin] = searcher.value.pos[0];
                }
            }
        }
        if (config.useCustomDictionary)
        {
            CustomDictionary.parseText(charArray, (begin, end, value) -> {
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
        }
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
                    List<AtomNode> atomNodeList = quickAtomSegment(charArray, i, j);
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
            Term term = new Term(new String(charArray, i, wordNet[i]), config.posTagging ? (natureArray[i] == null ? PosTag.nz : natureArray[i]) : null);
            term.offset = i;
            termList.add(term);
            i += wordNet[i];
        }
        return termList;
    }

    public DoubleArrayTrieSegment()
    {
        super();
        config.useCustomDictionary = false;
    }
}
