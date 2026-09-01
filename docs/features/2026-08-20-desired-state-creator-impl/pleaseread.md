이제부터 뼈대 클래스로만 있던 desiredstatecreator를 구현할거야.


이 친구의 역할이 뭐냐 하면 ScannedResources를 받아서 어노테이션을 처리하고, DesiredResources를 만드는것이야.

여기의 핵심은 내가 판단했을때는 어노테이션을 어떻게 처리할지인데, 이 부분을 좀 설명해줄게.

AllowSSH같은게 SecurityGroup에 있다고 생각해보자. 여기에서 중요한거는 AllowSSH 어노테이션이 본인이 SecurityGroup을 어떻게 수정할 지 간접적으로 안다가 핵심이야.

메타 어노테이션이 있고, 거기에는 handler.class가 있어. handler는 각 프로바이더가 어노테이션 별로 구현한 함수로, ScannedResourceState와 AllowSSH(매크로 어노테이션) 을 넘겨서 ScannedResourceState를 수정하는 방식이야.

이때 AllowSSH를 넘기는 이유는, 여기에 인자가 있을 수 있으니까. 예를 들어 AllowPort(port = 22) 이런게 있으면 어노테이션 원본이 있어야 하잖아. 그래서 그럼.


일단 이 정보를 토대로 plan을 작성해봐. 참고로 프로젝트 루트에 drawio xml도 있으니까 참고해도 되고.

이 plan을 바탕으로 내가 검토하고 spec을 작성하게 할거야. 
