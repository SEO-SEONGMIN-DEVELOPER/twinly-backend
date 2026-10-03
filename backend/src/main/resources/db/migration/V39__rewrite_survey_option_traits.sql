UPDATE persona_elements
SET explanation = '메시지를 한 줄씩 짧게 끊어 여러 개 보낸다'
WHERE dimension = 'COMMUNICATION_STYLE'
  AND explanation = '메시지를 한 줄씩 짧게 끊어 여러 개 보내고 짧은 호응을 자주 쓴다';

UPDATE persona_elements
SET explanation = '할 말을 한 메시지에 모아서 보낸다'
WHERE dimension = 'COMMUNICATION_STYLE'
  AND explanation = '메시지를 한 번에 길게 정리해서 보낸다';

UPDATE persona_elements
SET explanation = '약속 장소에 약속 시간보다 미리 도착한다'
WHERE dimension = 'CONSCIENTIOUSNESS'
  AND explanation = '약속 장소에 10분 일찍 도착해 기다린다';

UPDATE persona_elements
SET explanation = '술자리에 주 1회 이상 나간다'
WHERE dimension = 'LIFE_STYLE'
  AND explanation = '술자리에 주 1회 이상 나가고 약속이 잡히면 웬만하면 참석한다';

UPDATE persona_elements
SET explanation = '술자리가 주 1회 미만으로 드물거나 술을 아예 마시지 않는다'
WHERE dimension = 'LIFE_STYLE'
  AND explanation = '술자리에 월 1~2회만 나가고 가더라도 1차만 하고 일어난다';

UPDATE persona_elements
SET explanation = '주중에 보통 새벽 1시가 넘어서 잠든다'
WHERE dimension = 'LIFE_STYLE'
  AND explanation = '새벽 2~3시에 자고 오전 11시에 일어나며 밤에 주로 활동한다';

UPDATE persona_elements
SET explanation = '주중에 보통 새벽 1시 전에 잠든다'
WHERE dimension = 'LIFE_STYLE'
  AND explanation = '자정 전에 자고 오전 7~8시에 일어나며 아침에 주로 활동한다';

UPDATE persona_elements
SET explanation = '선 넘는 말을 들어도 그 자리에서는 그냥 넘어간다'
WHERE dimension = 'CONFLICT_STYLE'
  AND explanation = '선 넘는 말을 들으면 웃고 넘긴 뒤 그 사람과 거리를 둔다';

UPDATE persona_elements
SET explanation = '주 1회 이상 운동한다'
WHERE dimension = 'LIFE_STYLE'
  AND explanation = '주 3회 이상 헬스·러닝 같은 정해진 운동 루틴을 지킨다';

UPDATE persona_elements
SET explanation = '따로 하는 운동이 거의 없다'
WHERE dimension = 'LIFE_STYLE'
  AND explanation = '따로 운동하지 않고 웬만한 거리는 걸어 다닌다';

UPDATE persona_elements
SET explanation = '관심이 생기면 먼저 연락하거나 직접 표현한다'
WHERE dimension = 'COMMUNICATION_STYLE'
  AND explanation = '관심이 생기면 먼저 연락하고 다음 약속을 직접 제안한다';

UPDATE persona_elements
SET explanation = '관심이 생겨도 겉으로 티를 잘 내지 않는다'
WHERE dimension = 'COMMUNICATION_STYLE'
  AND explanation = '관심이 생겨도 티 내지 않고 상대의 이야기를 오래 듣고 기억한다';

UPDATE anon_session_persona_elements
SET explanation = '메시지를 한 줄씩 짧게 끊어 여러 개 보낸다'
WHERE dimension = 'COMMUNICATION_STYLE'
  AND explanation = '메시지를 한 줄씩 짧게 끊어 여러 개 보내고 짧은 호응을 자주 쓴다';

UPDATE anon_session_persona_elements
SET explanation = '할 말을 한 메시지에 모아서 보낸다'
WHERE dimension = 'COMMUNICATION_STYLE'
  AND explanation = '메시지를 한 번에 길게 정리해서 보낸다';

UPDATE anon_session_persona_elements
SET explanation = '약속 장소에 약속 시간보다 미리 도착한다'
WHERE dimension = 'CONSCIENTIOUSNESS'
  AND explanation = '약속 장소에 10분 일찍 도착해 기다린다';

UPDATE anon_session_persona_elements
SET explanation = '술자리에 주 1회 이상 나간다'
WHERE dimension = 'LIFE_STYLE'
  AND explanation = '술자리에 주 1회 이상 나가고 약속이 잡히면 웬만하면 참석한다';

UPDATE anon_session_persona_elements
SET explanation = '술자리가 주 1회 미만으로 드물거나 술을 아예 마시지 않는다'
WHERE dimension = 'LIFE_STYLE'
  AND explanation = '술자리에 월 1~2회만 나가고 가더라도 1차만 하고 일어난다';

UPDATE anon_session_persona_elements
SET explanation = '주중에 보통 새벽 1시가 넘어서 잠든다'
WHERE dimension = 'LIFE_STYLE'
  AND explanation = '새벽 2~3시에 자고 오전 11시에 일어나며 밤에 주로 활동한다';

UPDATE anon_session_persona_elements
SET explanation = '주중에 보통 새벽 1시 전에 잠든다'
WHERE dimension = 'LIFE_STYLE'
  AND explanation = '자정 전에 자고 오전 7~8시에 일어나며 아침에 주로 활동한다';

UPDATE anon_session_persona_elements
SET explanation = '선 넘는 말을 들어도 그 자리에서는 그냥 넘어간다'
WHERE dimension = 'CONFLICT_STYLE'
  AND explanation = '선 넘는 말을 들으면 웃고 넘긴 뒤 그 사람과 거리를 둔다';

UPDATE anon_session_persona_elements
SET explanation = '주 1회 이상 운동한다'
WHERE dimension = 'LIFE_STYLE'
  AND explanation = '주 3회 이상 헬스·러닝 같은 정해진 운동 루틴을 지킨다';

UPDATE anon_session_persona_elements
SET explanation = '따로 하는 운동이 거의 없다'
WHERE dimension = 'LIFE_STYLE'
  AND explanation = '따로 운동하지 않고 웬만한 거리는 걸어 다닌다';

UPDATE anon_session_persona_elements
SET explanation = '관심이 생기면 먼저 연락하거나 직접 표현한다'
WHERE dimension = 'COMMUNICATION_STYLE'
  AND explanation = '관심이 생기면 먼저 연락하고 다음 약속을 직접 제안한다';

UPDATE anon_session_persona_elements
SET explanation = '관심이 생겨도 겉으로 티를 잘 내지 않는다'
WHERE dimension = 'COMMUNICATION_STYLE'
  AND explanation = '관심이 생겨도 티 내지 않고 상대의 이야기를 오래 듣고 기억한다';
