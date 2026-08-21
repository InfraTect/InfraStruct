이제 ModuleRegistry에 대한 개발을 시작할거야.


각 프로바이더는 Provider 클래스를 상속해서 Aws 클래스와 같은 토큰을 만들고, 본인들이 만든 Validator와 Applier를 토큰에 묶어.

묶는 방법은 토큰에 RegisterProvider 어노테이션을 이용해서 필드에 class를 넣는거야.


그러면 InfraStruct.run()에서 ModuleRegistry가 현재 InfraStructApplication 어노테이션에 있는 provider 값을 참조해서 프로바이더 토큰을 가져오고, 거기에서 validator와 applier 클래스를 빼고,
오브젝트화해서 InfraStruct 클래스 필드에 저장하는거임.

이 정보와 InfraStruct drawio, 그리고 다른 브랜치??에 있는 코드를 토대로 plan을 작성해줘. dev에는 뼈대클래스가 있고, 다른 브랜치에서 작업해서 지금 모듈들은 구현이 완료되었고, pr 올린 상태임을 감안해줘.
