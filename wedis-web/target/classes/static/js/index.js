var v = new Vue({
    el: '#app',
    data: function () {
        return {
        	editConnection:{
        		title:'新建连接',
        		visible:false,
        		setting:{
        			id:'',
        			name:'',
        			host:'',
        			port:'',
        			pwd:''
        		}
        	},
        	selectConn:'',
        	conns:[
//        		{id:"1",name:'本地连接1'},
//        		{id:"2",name:"本地连接2"}
        	],
        	tabs:[
//        		{id:"11",label:"11",content:"11"}
        	],
        	activeTab:""
        };
    },
    methods: {
    	saveConnSetting: function(){
    		axios.post('/connection', this.editConnection.setting)
    			.then(function(resp){
    				let code = resp.data.code;
	    			if(code == 200){
	    				v.refreshConnList();
	    				v.editConnection.visible=false;
	    				v.$message('连接建立成功');
	    			}else if(code == 503){
						v.$message({message:'('+code +') "name"重复',type:'error'});
	    			}
    		}).catch(function(err){
				v.$message({message:err,type:'warning'});
    		});
    	},
    	installConnVo:function(){
    		var conn = {};
    		conn.name=this.editConnection.setting.name,
    		conn.host=this.editConnection.setting.host,
    		conn.port=this.editConnection.setting.port,
    		conn.pwd=this.editConnection.setting.pwd
    		return conn;
    	},
    	removeTab: function(targetId){
    		let tabs = this.tabs;
            if (this.activeTab === targetId) {
              tabs.forEach((tab, index) => {
                if (tab.id === targetId) {
                  let nextTab = tabs[index + 1] || tabs[index - 1];
                  if (nextTab) {
                	 this.activeTab = nextTab.id;
                	 return;
                  }
                }
              });
            }
            
            this.tabs = tabs.filter(tab => tab.id !== targetId);
    	},
    	openTab:function(tabId, tabName, url){
    		if(this.activeTab === tabId){
    			return;
    		}
    		var add = true;
    		this.tabs.forEach((tab, index)=>{
    			if(tab.id === tabId){
    				add = false;
    				return;
    			}
    		});
			this.activeTab = tabId;
    		if(add){
    			this.tabs.push({id:tabId,label:tabName,content:"<iframe src='"+url+"'>"})
    		}
    	},
    	connTest:function(){
    		axios.post('/connection/test', this.editConnection.setting)
				.then(function(resp){
					let code = resp.data.code;
					if(code == 200){
						v.$message({message:'连接成功',type:'success'});
					}else{
						v.$message({message:'('+code +') '+resp.data.msg,type:'error'});
					}
				}).catch(function(err){
					v.$message({message:err,type:'warning'});
			});
    	},
    	editConn(vm){
    		axios.get('/connection/'+this.selectConn).then(function(resp){
    			if(resp.data.code == 200){
    				let conn = resp.data.content;
    				v.editConnection.title='编辑连接';
    				v.editConnection.setting = conn;
    	    		v.editConnection.visible=true;
    			}else{
					v.$message({message:'未知错误',type:'warning'});
    			}
    		})
    	},
    	selectConnId(vnode){
    		this.selectConn = vnode.child.index;
    	},
    	refreshConnList(){
    		axios.get("/connection/list").then(function(resp){
    			if(resp.data.code == 200){
					v.conns = [];
    				resp.data.content.forEach((item, index)=>{
    					v.conns.push({id:item.id+"", name:item.name});
    				});
    			}
    		});
    	},
    	newConnection(){
    		this.editConnection.title='新建连接';
    		this.editConnection.visible=true;
    		this.editConnection.setting={
    			id:'',
    			name:'',
    			host:'localhost',
    			port:'6379',
    			pwd:''
    		};
    	}
    }
})

v.refreshConnList();