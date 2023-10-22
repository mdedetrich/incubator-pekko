/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.stream.javadsl.cookbook;

import static java.util.stream.Collectors.toList;
import static junit.framework.TestCase.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.japi.Function;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.stream.javadsl.SubSource;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class RecipeMultiGroupByTest extends RecipeTest {
  static ActorSystem system;

  @BeforeClass
  public static void setup() {
    system = ActorSystem.create("RecipeMultiGroupBy");
  }

  @AfterClass
  public static void tearDown() {
    TestKit.shutdownActorSystem(system);
    system = null;
  }

  static class Topic {
    private final String name;

    public Topic(String name) {
      this.name = name;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      Topic topic = (Topic) o;

      if (name != null ? !name.equals(topic.name) : topic.name != null) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      return name != null ? name.hashCode() : 0;
    }
  }

  @Test
  public void work() throws Exception {
    new TestKit(system) {
      final List<Topic> extractTopics(Message m) {
        final List<Topic> topics = new ArrayList<>(2);

        if (m.msg.startsWith("1")) {
          topics.add(new Topic("1"));
        } else {
          topics.add(new Topic("1"));
          topics.add(new Topic("2"));
        }

        return topics;
      }

      {
        final Source<Message, NotUsed> elems =
            Source.from(Arrays.asList("1: a", "1: b", "all: c", "all: d", "1: e"))
                .map(s -> new Message(s));

        // #multi-groupby
        final Function<Message, List<Topic>> topicMapper = m -> extractTopics(m);

        final Source<Pair<Message, Topic>, NotUsed> messageAndTopic =
            elems.mapConcat(
                (Message msg) -> {
                  List<Topic> topicsForMessage = topicMapper.apply(msg);
                  // Create a (Msg, Topic) pair for each of the topics

                  // the message belongs to
                  return topicsForMessage.stream()
                      .map(topic -> new Pair<Message, Topic>(msg, topic))
                      .collect(toList());
                });

        SubSource<Pair<Message, Topic>, NotUsed> multiGroups =
            messageAndTopic
                .groupBy(2, pair -> pair.second())
                .map(
                    pair -> {
                      Message message = pair.first();
                      Topic topic = pair.second();

                      // do what needs to be done
                      // #multi-groupby
                      return pair;
                      // #multi-groupby
                    });
        // #multi-groupby

        CompletionStage<List<String>> result =
            multiGroups
                .grouped(10)
                .mergeSubstreams()
                .map(
                    pair -> {
                      Topic topic = pair.get(0).second();
                      return topic.name
                          + mkString(
                              pair.stream().map(p -> p.first().msg).collect(toList()),
                              "[",
                              ", ",
                              "]");
                    })
                .grouped(10)
                .runWith(Sink.head(), system);

        List<String> got = result.toCompletableFuture().get(3, TimeUnit.SECONDS);
        assertTrue(got.contains("1[1: a, 1: b, all: c, all: d, 1: e]"));
        assertTrue(got.contains("2[all: c, all: d]"));
      }
    };
  }

  public static final String mkString(List<String> l, String start, String separate, String end) {
    StringBuilder sb = new StringBuilder(start);
    for (String s : l) {
      sb.append(s).append(separate);
    }
    return sb.delete(sb.length() - separate.length(), sb.length()).append(end).toString();
  }
}
