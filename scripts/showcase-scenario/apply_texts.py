# -*- coding: utf-8 -*-
"""build.py 가 만든 골격(시각·장소·동행·호감도)에 texts.json 의 장면 문장을 입힌다.

texts.json 은 장면마다 따로 쓴 문장이다. 키는 골격에서 바로 계산된다.
  행동: A|<userId>|<date>|<HH:MM>
  대화: D|<date>|<HH:MM>|<참가자 id 오름차순을 - 로 이음>
대화는 참가자 모두의 하루에 같은 내용으로 들어가고, 대사 시각은 골격의 시각을 그대로 쓴다.
골격과 키가 하나라도 어긋나면 파일을 쓰지 않고 멈춘다.
"""
import json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
SRC = os.path.join(REPO, "backend/src/main/resources/seed/showcase-scenarios.json")
TEXTS = os.path.join(HERE, "texts.json")


def action_key(day, scene):
    return f"A|{day['userId']}|{day['date']}|{scene['start'][11:16]}"


def dialogue_key(day, scene):
    members = sorted([day["userId"]] + scene["with"], key=int)
    return f"D|{day['date']}|{scene['start'][11:16]}|{'-'.join(members)}"


def apply(doc, texts):
    actions, dialogues = texts["actions"], texts["conversations"]
    problems, used = [], set()
    for day in doc["days"]:
        for scene in day["scenes"]:
            if scene["type"] == "action":
                key = action_key(day, scene)
                text = actions.get(key)
                if text is None:
                    problems.append(f"문장 없음: {key}")
                    continue
                scene["narration"] = text["narration"]
                scene["mind"] = text["mind"]
            else:
                key = dialogue_key(day, scene)
                text = dialogues.get(key)
                slots = [line["occursAt"] for line in scene["lines"]]
                if text is None or len(text["lines"]) != len(slots) - 1:
                    problems.append(f"대사 없음 또는 줄 수 불일치: {key}")
                    continue
                scene["lines"] = [{"t": "narr", "text": text["narr"], "occursAt": slots[0]}] + [
                    {"t": "bubble", "userId": line["speaker"].lstrip("u"), "action": line["action"],
                     "text": line["text"], "occursAt": at}
                    for line, at in zip(text["lines"], slots[1:])]
            used.add(key)
    unused = (actions.keys() | dialogues.keys()) - used
    problems += [f"골격에 없는 키: {key}" for key in sorted(unused)]
    return problems


if __name__ == "__main__":
    doc = json.load(open(SRC, encoding="utf-8"))
    problems = apply(doc, json.load(open(TEXTS, encoding="utf-8")))
    if problems:
        print(f"적용 중단: {len(problems)}건")
        for p in problems[:20]:
            print("   ", p)
        sys.exit(1)
    json.dump(doc, open(SRC, "w", encoding="utf-8"), ensure_ascii=False)
    print("문장 적용 완료:", len(doc["days"]), "일")
